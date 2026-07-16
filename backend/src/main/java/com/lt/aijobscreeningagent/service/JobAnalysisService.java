package com.lt.aijobscreeningagent.service;

import com.lt.aijobscreeningagent.config.LlmProperties;
import com.lt.aijobscreeningagent.dto.JobAnalyzeRequest;
import com.lt.aijobscreeningagent.dto.JobAnalyzeProfileRag;
import com.lt.aijobscreeningagent.dto.JobAnalyzeProfileRagChunk;
import com.lt.aijobscreeningagent.dto.JobAnalyzeResponse;
import com.lt.aijobscreeningagent.dto.JobRecordSummary;
import com.lt.aijobscreeningagent.dto.LlmAnalyzeResult;
import com.lt.aijobscreeningagent.llm.LlmClient;
import com.lt.aijobscreeningagent.profile.ProfileSearchChunkResponse;
import com.lt.aijobscreeningagent.profile.RagRetrievalService;
import com.lt.aijobscreeningagent.profile.UserProfileRagService;
import com.lt.aijobscreeningagent.repository.JobRecordRepository;
import com.lt.aijobscreeningagent.service.rag.JobQueryBuilder;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JobAnalysisService {

  private static final Logger log = LoggerFactory.getLogger(JobAnalysisService.class);
  private static final String DEFAULT_PROFILE_NAME = "default";
  private static final int PROFILE_RAG_TOP_K = 5;

  private final LlmProperties llmProperties;
  private final LlmClient llmClient;
  private final JobRecordRepository jobRecordRepository;
  private final JobAnalysisCacheService jobAnalysisCacheService;
  private final UserProfileRagService userProfileRagService;
  private final RagRetrievalService ragRetrievalService;
  private final JobQueryBuilder jobQueryBuilder;

  public JobAnalysisService(
      LlmProperties llmProperties,
      LlmClient llmClient,
      JobRecordRepository jobRecordRepository,
      JobAnalysisCacheService jobAnalysisCacheService,
      UserProfileRagService userProfileRagService,
      RagRetrievalService ragRetrievalService,
      JobQueryBuilder jobQueryBuilder
  ) {
    this.llmProperties = llmProperties;
    this.llmClient = llmClient;
    this.jobRecordRepository = jobRecordRepository;
    this.jobAnalysisCacheService = jobAnalysisCacheService;
    this.userProfileRagService = userProfileRagService;
    this.ragRetrievalService = ragRetrievalService;
    this.jobQueryBuilder = jobQueryBuilder;
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
    long jobRecordId = request.capturedJobRecordId() != null
        && jobRecordRepository.existsJobRecord(request.capturedJobRecordId())
        ? request.capturedJobRecordId()
        : jobRecordRepository.saveJobRecord(request);
    if (request.capturedJobRecordId() != null && jobRecordId == request.capturedJobRecordId()) {
      jobRecordRepository.updateRuleResult(jobRecordId, request.ruleScore(), request.ruleConclusion());
    }
    ProfileRagContext profileRagContext = buildProfileRagContext(request, profileVersion);
    JobAnalyzeResponse response;

    if (!llmProperties.isEnabled() || !llmProperties.hasApiKey()) {
      response = fallbackAnalyze(jobRecordId, taskId, request, profileRagContext.profileRag());
      jobRecordRepository.saveJobAnalysis(jobRecordId, response);
      jobAnalysisCacheService.put(cacheKey, response);
      return response;
    }

    try {
      LlmAnalyzeResult result = llmClient.analyze(request, profileRagContext.promptContext());
      response = new JobAnalyzeResponse(
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
          profileRagContext.profileRag()
      );
    } catch (RuntimeException e) {
      response = fallbackAnalyze(jobRecordId, taskId, request, profileRagContext.profileRag());
    }

    jobRecordRepository.saveJobAnalysis(jobRecordId, response);
    jobAnalysisCacheService.put(cacheKey, response);
    return response;
  }

  public List<JobRecordSummary> findRecentRecords(Integer limit) {
    return jobRecordRepository.findRecentRecords(limit);
  }

  private JobAnalyzeResponse fallbackAnalyze(Long jobRecordId, String taskId, JobAnalyzeRequest request) {
    return fallbackAnalyze(jobRecordId, taskId, request, null);
  }

  private JobAnalyzeResponse fallbackAnalyze(
      Long jobRecordId,
      String taskId,
      JobAnalyzeRequest request,
      JobAnalyzeProfileRag profileRag
  ) {
    int score = request.ruleScore() != null ? request.ruleScore() : 72;
    String decision = request.ruleConclusion() != null && !request.ruleConclusion().isBlank()
        ? request.ruleConclusion()
        : "可投";
    String direction = detectMockDirection(request);

    return new JobAnalyzeResponse(
        jobRecordId,
        taskId,
        "fallback",
        decision,
        score,
        direction,
        List.of(
            "当前结果为 fallback 分析：LLM 未启用、未配置 key、调用失败或返回内容解析失败。",
            "岗位文本中包含后端或 AI 应用相关信息，可先加入人工复核列表。",
            "建议结合 Excel 跟进表中的投递状态、沟通状态和面试记录继续判断。"
        ),
        List.of(
            "请人工确认出勤周期、实习时长和岗位方向是否与个人计划匹配。",
            "如果 JD 描述较短，评分结果只作为初筛参考。"
        ),
        List.of(
            "Java/Spring/MySQL/Redis 或 AI 应用项目经历",
            "接口开发、问题排查、业务理解与沟通能力",
            "可结合个人项目经历补充岗位相关证据"
        ),
        List.of(
            "介绍一个与岗位技术栈相关的后端或 AI 应用项目",
            "说明接口设计、数据库设计或服务联调中的具体工作",
            "准备回答为什么选择该岗位方向"
        ),
        "您好，我对这个岗位比较感兴趣，也有相关后端开发/AI 应用项目经历，想进一步了解岗位的具体工作内容和实习安排。",
        profileRag
    );
  }

  private String detectMockDirection(JobAnalyzeRequest request) {
    String text = String.join(" ",
        valueOrEmpty(request.jobTitle()),
        valueOrEmpty(request.jobText())
    );

    if (text.matches("(?i).*?(AI|Agent|RAG|大模型|智能体).*")) {
      return "AI应用后端";
    }
    if (text.matches("(?i).*?(Java|Spring|MySQL|Redis|后端).*")) {
      return "Java后端";
    }
    return "待人工确认";
  }

  private String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }

  private String loadProfileVersion() {
    try {
      return userProfileRagService.profileVersion(DEFAULT_PROFILE_NAME);
    } catch (RuntimeException e) {
      log.warn("Failed to load profile version, use fallback cache namespace.");
      return "profile-version-unavailable";
    }
  }

  private ProfileRagContext buildProfileRagContext(JobAnalyzeRequest request, String profileVersion) {
    String profileQuery = buildProfileQuery(request);
    try {
      log.info("Profile RAG query: {}", abbreviate(profileQuery, 300));
      RagRetrievalService.RagRetrievalResult retrieval = ragRetrievalService.retrieveWithTrace(
          DEFAULT_PROFILE_NAME,
          profileQuery,
          PROFILE_RAG_TOP_K
      );
      List<ProfileSearchChunkResponse> chunks = retrieval.chunks();
      log.info("Profile RAG retrieved {} chunks.", chunks.size());
      JobAnalyzeProfileRag profileRag = new JobAnalyzeProfileRag(
          true,
          profileVersion,
          profileQuery,
          chunks.size(),
          toResponseChunks(chunks),
          chunks.isEmpty() ? "No matched profile chunks." : null,
          retrieval.mode()
      );
      return new ProfileRagContext(formatProfileContext(chunks), profileRag);
    } catch (RuntimeException e) {
      log.warn("Profile RAG search failed, continue analyze without profile chunks.");
      JobAnalyzeProfileRag profileRag = new JobAnalyzeProfileRag(
          false,
          profileVersion,
          profileQuery,
          0,
          List.of(),
          "Profile RAG search failed, analyze continued without profile chunks.",
          "FALLBACK_KEYWORD"
      );
      return new ProfileRagContext(noProfileContext(), profileRag);
    }
  }

  private String buildProfileQuery(JobAnalyzeRequest request) {
    if (request.capturedJobRecordId() != null) {
      try {
        var structured = jobRecordRepository.findStructuredJobInfo(request.capturedJobRecordId());
        if (structured.isPresent()) {
          return jobQueryBuilder.build(structured.get());
        }
      } catch (RuntimeException e) {
        log.warn("Could not load structured job record for profile query; using request fields.");
      }
    }
    return jobQueryBuilder.build(request);
  }

  private String formatProfileContext(List<ProfileSearchChunkResponse> chunks) {
    if (chunks == null || chunks.isEmpty()) {
      return noProfileContext();
    }

    StringBuilder builder = new StringBuilder("【用户画像检索资料】\n");
    for (int i = 0; i < chunks.size(); i++) {
      ProfileSearchChunkResponse chunk = chunks.get(i);
      builder.append("资料").append(i + 1).append("：\n");
      builder.append("标题：").append(valueOrEmpty(chunk.title())).append("\n");
      builder.append("内容：").append(valueOrEmpty(chunk.content())).append("\n");
      builder.append("来源：").append(valueOrEmpty(chunk.sourceType())).append("\n");
      builder.append("匹配分：").append(chunk.score()).append("\n\n");
    }
    return builder.toString().trim();
  }

  private String noProfileContext() {
    return """
        【用户画像检索资料】
        未检索到相关用户画像资料，请仅基于岗位信息、规则评分和已有背景进行保守分析，不要编造用户经历。
        """;
  }

  private List<JobAnalyzeProfileRagChunk> toResponseChunks(List<ProfileSearchChunkResponse> chunks) {
    if (chunks == null || chunks.isEmpty()) {
      return List.of();
    }
    return chunks.stream()
        .limit(PROFILE_RAG_TOP_K)
        .map(chunk -> new JobAnalyzeProfileRagChunk(
            chunk.id(),
            valueOrEmpty(chunk.title()),
            abbreviate(valueOrEmpty(chunk.content()), 200),
            chunk.score(),
            valueOrEmpty(chunk.sourceType()),
            valueOrEmpty(chunk.chunkType()),
            chunk.semanticScore(),
            chunk.keywordScore(),
            chunk.finalScore()
        ))
        .toList();
  }

  private String abbreviate(String value, int maxLength) {
    String normalized = valueOrEmpty(value).replaceAll("\\s+", " ").trim();
    if (normalized.length() <= maxLength) {
      return normalized;
    }
    return normalized.substring(0, maxLength) + "...";
  }

  private record ProfileRagContext(
      String promptContext,
      JobAnalyzeProfileRag profileRag
  ) {
  }
}
