package com.lt.aijobscreeningagent.service;

import com.lt.aijobscreeningagent.dto.JobAnalyzeProfileRag;
import com.lt.aijobscreeningagent.dto.JobAnalyzeProfileRagChunk;
import com.lt.aijobscreeningagent.dto.JobAnalyzeRequest;
import com.lt.aijobscreeningagent.dto.JobAnalyzeResponse;
import com.lt.aijobscreeningagent.dto.JobRecordSummary;
import com.lt.aijobscreeningagent.profile.ProfileSearchChunkResponse;
import com.lt.aijobscreeningagent.profile.RagRetrievalService;
import com.lt.aijobscreeningagent.profile.UserProfileRagService;
import com.lt.aijobscreeningagent.repository.JobRecordRepository;
import com.lt.aijobscreeningagent.service.analysis.AnalysisSaver;
import com.lt.aijobscreeningagent.service.analysis.FallbackAnalysisService;
import com.lt.aijobscreeningagent.service.analysis.JobAnalysisPrompt;
import com.lt.aijobscreeningagent.service.analysis.JobAnalysisPromptBuilder;
import com.lt.aijobscreeningagent.service.analysis.JobRuleEngine;
import com.lt.aijobscreeningagent.service.analysis.LlmAnalyzer;
import com.lt.aijobscreeningagent.service.analysis.RagContext;
import com.lt.aijobscreeningagent.service.analysis.RagContextBuilder;
import com.lt.aijobscreeningagent.service.analysis.ResultValidator;
import com.lt.aijobscreeningagent.service.analysis.RuleAnalysisResult;
import com.lt.aijobscreeningagent.service.rag.JobQueryBuilder;
import com.lt.aijobscreeningagent.service.trace.JobAnalysisTraceService;
import com.lt.aijobscreeningagent.service.trace.JobAnalysisTraceService.TraceHandle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates one job-analysis request. Business rules, retrieval context,
 * prompt construction, LLM invocation, validation and persistence are kept in
 * dedicated collaborators so the existing API and cache behavior remain stable.
 */
@Service
public class JobAnalysisService {

  private static final Logger log = LoggerFactory.getLogger(JobAnalysisService.class);
  private static final String DEFAULT_PROFILE_NAME = "default";
  private static final int PROFILE_RAG_TOP_K = 5;
  private static final String PROMPT_VERSION = "v1";

  private final JobRecordRepository jobRecordRepository;
  private final JobAnalysisCacheService jobAnalysisCacheService;
  private final UserProfileRagService userProfileRagService;
  private final RagRetrievalService ragRetrievalService;
  private final JobQueryBuilder jobQueryBuilder;
  private final JobRuleEngine jobRuleEngine;
  private final RagContextBuilder ragContextBuilder;
  private final JobAnalysisPromptBuilder promptBuilder;
  private final LlmAnalyzer llmAnalyzer;
  private final ResultValidator resultValidator;
  private final FallbackAnalysisService fallbackAnalysisService;
  private final AnalysisSaver analysisSaver;
  private final JobAnalysisTraceService traceService;

  public JobAnalysisService(
      JobRecordRepository jobRecordRepository,
      JobAnalysisCacheService jobAnalysisCacheService,
      UserProfileRagService userProfileRagService,
      RagRetrievalService ragRetrievalService,
      JobQueryBuilder jobQueryBuilder,
      JobRuleEngine jobRuleEngine,
      RagContextBuilder ragContextBuilder,
      JobAnalysisPromptBuilder promptBuilder,
      LlmAnalyzer llmAnalyzer,
      ResultValidator resultValidator,
      FallbackAnalysisService fallbackAnalysisService,
      AnalysisSaver analysisSaver,
      JobAnalysisTraceService traceService
  ) {
    this.jobRecordRepository = jobRecordRepository;
    this.jobAnalysisCacheService = jobAnalysisCacheService;
    this.userProfileRagService = userProfileRagService;
    this.ragRetrievalService = ragRetrievalService;
    this.jobQueryBuilder = jobQueryBuilder;
    this.jobRuleEngine = jobRuleEngine;
    this.ragContextBuilder = ragContextBuilder;
    this.promptBuilder = promptBuilder;
    this.llmAnalyzer = llmAnalyzer;
    this.resultValidator = resultValidator;
    this.fallbackAnalysisService = fallbackAnalysisService;
    this.analysisSaver = analysisSaver;
    this.traceService = traceService;
  }

  public JobAnalyzeResponse analyze(JobAnalyzeRequest request) {
    String profileVersion = loadProfileVersion();
    String cacheKey = jobAnalysisCacheService.buildCacheKey(request, profileVersion);
    var cachedResponse = request.capturedJobRecordId() == null
        ? jobAnalysisCacheService.get(cacheKey)
        : java.util.Optional.<JobAnalyzeResponse>empty();
    if (cachedResponse.isPresent()) {
      log.info("Job analyze Redis cache hit. profileVersion={}", profileVersion);
      return cachedResponse.get();
    }
    log.info("Job analyze Redis cache miss. profileVersion={}", profileVersion);

    String taskId = UUID.randomUUID().toString();
    long jobRecordId = resolveJobRecord(request);
    TraceHandle ruleTrace = traceService.start(taskId, jobRecordId, "RULE_ANALYSIS", request);
    RuleAnalysisResult rules;
    try {
      rules = jobRuleEngine.evaluate(request);
      traceService.finish(ruleTrace, rules);
    } catch (RuntimeException ex) {
      traceService.fail(ruleTrace, ex);
      throw ex;
    }

    ProfileRagContext profileRag = retrieveProfileContext(request, profileVersion, taskId, jobRecordId);

    TraceHandle promptTrace = traceService.start(taskId, jobRecordId, "PROMPT_BUILD",
        Map.of("promptVersion", PROMPT_VERSION));
    JobAnalysisPrompt prompt;
    try {
      prompt = promptBuilder.build(request, rules, profileRag.context());
      Map<String, Object> promptOutput = new LinkedHashMap<>();
      promptOutput.put("success", true);
      promptOutput.put("promptVersion", PROMPT_VERSION);
      promptOutput.put("promptLength", estimatePromptLength(prompt));
      promptOutput.put("feedbackChunkCount", countFeedbackChunks(profileRag.context().chunks()));
      traceService.finish(promptTrace, promptOutput);
    } catch (RuntimeException ex) {
      traceService.fail(promptTrace, ex);
      throw ex;
    }

    JobAnalyzeResponse response;
    TraceHandle llmTrace = traceService.start(taskId, jobRecordId, "LLM_CALL",
        Map.of("model", llmAnalyzer.model(), "promptVersion", PROMPT_VERSION));
    if (!llmAnalyzer.enabled()) {
      traceService.finish(llmTrace, Map.of(
          "success", false,
          "skipped", true,
          "reason", "LLM disabled or API key is empty",
          "model", llmAnalyzer.model(),
          "responseLength", 0));
      recordValidationSkipped(taskId, jobRecordId, "LLM disabled or API key is empty");
      response = fallbackAnalysisService.create(jobRecordId, taskId, request, rules, profileRag.profileRag());
    } else {
      com.lt.aijobscreeningagent.dto.LlmAnalyzeResult llmResult = null;
      try {
        llmResult = llmAnalyzer.analyze(prompt);
        traceService.finish(llmTrace, Map.of(
            "success", true,
            "model", llmAnalyzer.model(),
            "responseLength", traceService.jsonLength(llmResult)));
      } catch (RuntimeException ex) {
        traceService.fail(llmTrace, ex);
        log.warn("LLM analysis failed; returning fallback response.", ex);
      }

      if (llmResult == null) {
        recordValidationSkipped(taskId, jobRecordId, "LLM call failed");
        response = fallbackAnalysisService.create(jobRecordId, taskId, request, rules, profileRag.profileRag());
      } else {
        TraceHandle validationTrace = traceService.start(taskId, jobRecordId, "RESULT_VALIDATE",
            Map.of("responseLength", traceService.jsonLength(llmResult)));
        try {
          var validated = resultValidator.validate(llmResult);
          traceService.finish(validationTrace, Map.of("success", true, "validated", true));
          response = toResponse(jobRecordId, taskId, validated, profileRag.profileRag());
        } catch (RuntimeException ex) {
          traceService.fail(validationTrace, ex);
          log.warn("LLM result validation failed; returning fallback response.", ex);
          response = fallbackAnalysisService.create(jobRecordId, taskId, request, rules, profileRag.profileRag());
        }
      }
    }

    TraceHandle saveTrace = traceService.start(taskId, jobRecordId, "SAVE_RESULT",
        Map.of("jobRecordId", jobRecordId, "status", response.status()));
    try {
      analysisSaver.save(jobRecordId, response, cacheKey);
      traceService.finish(saveTrace, Map.of("success", true, "jobRecordId", jobRecordId));
    } catch (RuntimeException ex) {
      traceService.fail(saveTrace, ex);
      throw ex;
    }
    return response;
  }

  public List<JobRecordSummary> findRecentRecords(Integer limit) {
    return jobRecordRepository.findRecentRecords(limit);
  }

  private long resolveJobRecord(JobAnalyzeRequest request) {
    if (request.capturedJobRecordId() != null
        && jobRecordRepository.existsJobRecord(request.capturedJobRecordId())) {
      jobRecordRepository.updateRuleResult(
          request.capturedJobRecordId(), request.ruleScore(), request.ruleConclusion());
      return request.capturedJobRecordId();
    }
    return jobRecordRepository.saveJobRecord(request);
  }

  private ProfileRagContext retrieveProfileContext(
      JobAnalyzeRequest request, String profileVersion, String taskId, long jobRecordId) {
    TraceHandle ragTrace = traceService.start(taskId, jobRecordId, "RAG_RETRIEVAL", traceInput(request));
    String profileQuery = "";
    try {
      profileQuery = buildProfileQuery(request);
      log.info("Profile RAG query: {}", abbreviate(profileQuery, 300));
      RagRetrievalService.RagRetrievalResult retrieval = ragRetrievalService.retrieveWithTrace(
          DEFAULT_PROFILE_NAME, profileQuery, PROFILE_RAG_TOP_K);
      RagContext context = ragContextBuilder.build(retrieval.chunks());
      List<ProfileSearchChunkResponse> chunks = retrieval.chunks();
      JobAnalyzeProfileRag profileRag = new JobAnalyzeProfileRag(
          true,
          profileVersion,
          profileQuery,
          chunks.size(),
          toResponseChunks(chunks),
          chunks.isEmpty() ? "No matched profile chunks." : null,
          retrieval.mode());
      log.info("Profile RAG retrieved {} chunks. mode={}", chunks.size(), retrieval.mode());
      traceService.finish(ragTrace, ragOutput(profileQuery, retrieval));
      return new ProfileRagContext(context, profileRag);
    } catch (RuntimeException ex) {
      traceService.fail(ragTrace, ex);
      log.warn("Profile RAG search failed; continue without profile chunks.", ex);
      JobAnalyzeProfileRag profileRag = new JobAnalyzeProfileRag(
          false,
          profileVersion,
          profileQuery,
          0,
          List.of(),
          "Profile RAG search failed, analyze continued without profile chunks.",
          "FALLBACK_KEYWORD");
      return new ProfileRagContext(ragContextBuilder.build(List.of()), profileRag);
    }
  }

  private Map<String, Object> traceInput(JobAnalyzeRequest request) {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("profileName", DEFAULT_PROFILE_NAME);
    input.put("topK", PROFILE_RAG_TOP_K);
    input.put("jobTitle", request == null ? null : request.jobTitle());
    input.put("jobTextLength", request == null || request.jobText() == null ? 0 : request.jobText().length());
    return input;
  }

  private void recordValidationSkipped(String taskId, long jobRecordId, String reason) {
    TraceHandle validationTrace = traceService.start(taskId, jobRecordId, "RESULT_VALIDATE",
        Map.of("skipped", true));
    traceService.finish(validationTrace, Map.of("success", false, "skipped", true, "reason", reason));
  }

  private Map<String, Object> ragOutput(
      String query, RagRetrievalService.RagRetrievalResult retrieval) {
    Map<String, Object> output = new LinkedHashMap<>();
    output.put("success", true);
    output.put("query", query);
    output.put("retrievalMode", retrieval.mode());
    output.put("chunkCount", retrieval.chunks().size());
    List<Map<String, Object>> feedbackChunks = retrieval.chunks().stream()
        .filter(chunk -> chunk != null && "FEEDBACK".equalsIgnoreCase(chunk.chunkType()))
        .map(chunk -> {
          Map<String, Object> trace = new LinkedHashMap<>();
          trace.put("id", chunk.id());
          trace.put("chunkType", chunk.chunkType());
          trace.put("chunkWeight", chunk.chunkWeight());
          trace.put("finalScore", chunk.finalScore());
          return trace;
        })
        .toList();
    output.put("feedbackChunks", feedbackChunks);
    if (!retrieval.chunks().isEmpty()) {
      ProfileSearchChunkResponse top = retrieval.chunks().get(0);
      output.put("topChunkId", top.id());
      output.put("chunkType", top.chunkType());
      output.put("semanticScore", top.semanticScore());
      output.put("keywordScore", top.keywordScore());
      output.put("chunkWeight", top.chunkWeight());
      output.put("baseScore", top.baseScore());
      output.put("finalScore", top.finalScore());
    }
    return output;
  }

  private long countFeedbackChunks(List<ProfileSearchChunkResponse> chunks) {
    if (chunks == null) {
      return 0L;
    }
    return chunks.stream()
        .filter(chunk -> chunk != null && "FEEDBACK".equalsIgnoreCase(chunk.chunkType()))
        .count();
  }

  private int estimatePromptLength(JobAnalysisPrompt prompt) {
    JobAnalyzeRequest request = prompt.request();
    int requestLength = request == null ? 0 : String.join(" ",
        valueOrEmpty(request.jobTitle()),
        valueOrEmpty(request.companyName()),
        valueOrEmpty(request.salary()),
        valueOrEmpty(request.city()),
        valueOrEmpty(request.schedule()),
        valueOrEmpty(request.duration()),
        valueOrEmpty(request.jobText()),
        valueOrEmpty(request.ruleConclusion())).length();
    return requestLength + (prompt.ragContext() == null || prompt.ragContext().text() == null
        ? 0 : prompt.ragContext().text().length());
  }

  private String buildProfileQuery(JobAnalyzeRequest request) {
    if (request.capturedJobRecordId() != null) {
      try {
        var structured = jobRecordRepository.findStructuredJobInfo(request.capturedJobRecordId());
        if (structured.isPresent()) {
          return jobQueryBuilder.build(structured.get(), request);
        }
      } catch (RuntimeException ex) {
        log.warn("Could not load structured job record for profile query; using request fields.", ex);
      }
    }
    return jobQueryBuilder.build(request);
  }

  private JobAnalyzeResponse toResponse(
      long jobRecordId,
      String taskId,
      com.lt.aijobscreeningagent.dto.LlmAnalyzeResult result,
      JobAnalyzeProfileRag profileRag
  ) {
    return new JobAnalyzeResponse(
        jobRecordId,
        taskId,
        "success",
        result.decision(),
        result.score(),
        result.direction(),
        result.reasons(),
        result.risks(),
        result.resumeMatches(),
        result.interviewFocus(),
        result.suggestedMessage(),
        profileRag);
  }

  private List<JobAnalyzeProfileRagChunk> toResponseChunks(List<ProfileSearchChunkResponse> chunks) {
    if (chunks == null || chunks.isEmpty()) {
      return List.of();
    }
    return chunks.stream().limit(PROFILE_RAG_TOP_K).map(chunk -> new JobAnalyzeProfileRagChunk(
        chunk.id(),
        valueOrEmpty(chunk.title()),
        abbreviate(valueOrEmpty(chunk.content()), 200),
        chunk.score(),
        valueOrEmpty(chunk.sourceType()),
        valueOrEmpty(chunk.chunkType()),
        chunk.semanticScore(),
        chunk.keywordScore(),
        chunk.finalScore(),
        chunk.chunkWeight(),
        chunk.baseScore())).toList();
  }

  private String loadProfileVersion() {
    try {
      return userProfileRagService.profileVersion(DEFAULT_PROFILE_NAME);
    } catch (RuntimeException ex) {
      log.warn("Failed to load profile version; use fallback cache namespace.", ex);
      return "profile-version-unavailable";
    }
  }

  private String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }

  private String abbreviate(String value, int maxLength) {
    String normalized = valueOrEmpty(value).replaceAll("\\s+", " ").trim();
    return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
  }

  private record ProfileRagContext(RagContext context, JobAnalyzeProfileRag profileRag) {
  }
}
