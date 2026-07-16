package com.lt.aijobscreeningagent.profile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import com.lt.aijobscreeningagent.dto.JobHistoryRecord;
import com.lt.aijobscreeningagent.repository.JobHistoryRepository;
import com.lt.aijobscreeningagent.service.JobFieldSanitizer;
import com.lt.aijobscreeningagent.service.feedback.FeedbackChunkBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserProfileRagService {

  private static final int DEFAULT_TOP_K = 5;
  private static final int MAX_TOP_K = 20;

  private final UserProfileRepository userProfileRepository;
  private final UserProfileRagRepository userProfileRagRepository;
  private final JobHistoryRepository jobHistoryRepository;
  private final JobFieldSanitizer jobFieldSanitizer;
  private final ProfileEmbeddingIndexService profileEmbeddingIndexService;
  private final FeedbackChunkBuilder feedbackChunkBuilder;

  public UserProfileRagService(
      UserProfileRepository userProfileRepository,
      UserProfileRagRepository userProfileRagRepository,
      JobHistoryRepository jobHistoryRepository,
      JobFieldSanitizer jobFieldSanitizer,
      ProfileEmbeddingIndexService profileEmbeddingIndexService,
      FeedbackChunkBuilder feedbackChunkBuilder
  ) {
    this.userProfileRepository = userProfileRepository;
    this.userProfileRagRepository = userProfileRagRepository;
    this.jobHistoryRepository = jobHistoryRepository;
    this.jobFieldSanitizer = jobFieldSanitizer;
    this.profileEmbeddingIndexService = profileEmbeddingIndexService;
    this.feedbackChunkBuilder = feedbackChunkBuilder;
  }

  @Transactional
  public ProfileReindexResponse reindexDefaultProfile() {
    return reindexDefaultProfile(false);
  }

  @Transactional
  public ProfileReindexResponse reindexDefaultProfile(boolean includeHistory) {
    UserProfile profile = userProfileRepository.findDefault()
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Default user profile is not initialized"
        ));
    String profileName = profile.profileName();

    List<ChunkDraft> chunks = new ArrayList<>(buildChunkDrafts(profile));
    // Feedback memory is part of the profile index regardless of the optional
    // historical-analysis flag. It is rebuilt from the source table so a full
    // profile reindex does not lose the behavior memory chunks.
    chunks.addAll(buildFeedbackChunkDrafts());
    if (includeHistory) {
      chunks.addAll(buildHistoryChunkDrafts());
    }
    String rawText = buildRawText(chunks);

    UserProfileRagRepository.DeletedIndexCount deletedIndexCount =
        userProfileRagRepository.deleteProfileIndex(profileName);
    Long documentId = userProfileRagRepository.saveDocument(profileName, rawText, sha256(rawText));

    int index = 0;
    for (ChunkDraft chunk : chunks) {
      String content = normalize(chunk.content());
      if (content.isBlank()) {
        continue;
      }
      userProfileRagRepository.saveChunk(
          profileName,
          documentId,
          index,
          chunk.title(),
          content,
          sha256(chunk.title() + "\n" + content),
          scoreHint(chunk.title()),
          chunk.sourceType(),
          chunk.chunkType(),
          chunk.chunkWeight(),
          chunk.metadataJson()
      );
      index++;
    }

    try {
      profileEmbeddingIndexService.reindex(profileName);
    } catch (RuntimeException e) {
      // Embedding is an enhancement; a provider outage must not break keyword RAG.
      org.slf4j.LoggerFactory.getLogger(UserProfileRagService.class)
          .warn("Profile embedding index failed, keyword retrieval remains available.", e);
    }

    return new ProfileReindexResponse(
        true,
        profileName,
        documentId,
        index,
        deletedIndexCount.deletedDocumentCount(),
        deletedIndexCount.deletedChunkCount()
    );
  }

  public ProfileSearchResponse searchDefaultProfile(String query, Integer topK) {
    int limit = normalizeTopK(topK);
    String normalizedQuery = normalize(query);
    if (normalizedQuery.isBlank()) {
      return new ProfileSearchResponse(false, "", limit, List.of());
    }

    List<ProfileSearchChunkResponse> chunks = searchProfileChunks("default", normalizedQuery, limit);
    return new ProfileSearchResponse(!userProfileRagRepository.findDefaultChunks().isEmpty(), normalizedQuery, limit, chunks);
  }

  public List<ProfileSearchChunkResponse> searchProfileChunks(String profileName, String query, int topK) {
    int limit = normalizeTopK(topK);
    String normalizedQuery = normalize(query);
    if (normalizedQuery.isBlank()) {
      return List.of();
    }

    List<String> terms = tokenize(normalizedQuery);
    UserProfile profile = userProfileRepository.findDefault().orElse(null);
    List<String> positiveTerms = tokenize(profile == null ? "" : profile.positiveKeywords());
    List<String> negativeTerms = tokenize(profile == null ? "" : profile.negativeKeywords());
    List<UserProfileChunk> indexedChunks = userProfileRagRepository.findChunksByProfileName(profileName);
    return indexedChunks.stream()
        .map(chunk -> toSearchResponse(chunk, terms, positiveTerms, negativeTerms))
        .filter(chunk -> chunk.score() != 0)
        .sorted(Comparator.comparingInt(ProfileSearchChunkResponse::score).reversed()
            .thenComparing(ProfileSearchChunkResponse::id))
        .limit(limit)
        .toList();
  }

  public String profileVersion(String profileName) {
    return userProfileRagRepository.findLatestProfileVersion(profileName)
        .orElse("no-profile-index");
  }

  private ProfileSearchChunkResponse toSearchResponse(
      UserProfileChunk chunk,
      List<String> terms,
      List<String> positiveTerms,
      List<String> negativeTerms
  ) {
    String title = normalize(chunk.title());
    String content = normalize(chunk.content());
    String titleLower = title.toLowerCase(Locale.ROOT);
    String contentLower = content.toLowerCase(Locale.ROOT);
    int score = 0;

    for (String term : terms) {
      String lower = term.toLowerCase(Locale.ROOT);
      if (titleLower.contains(lower)) {
        score += 2;
      }
      if (contentLower.contains(lower)) {
        score += 1;
      }
      if (positiveTerms.contains(term) && (titleLower.contains(lower) || contentLower.contains(lower))) {
        score += 2;
      }
      if (negativeTerms.contains(term) && (titleLower.contains(lower) || contentLower.contains(lower))) {
        score -= 3;
      }
    }
    if (score > 0 && chunk.scoreHint() != null) {
      score += chunk.scoreHint();
    }

    // Weight is applied once by RagRetrievalService after both evidence
    // channels have been normalized to the same 0..1 scale.
    double weight = chunk.chunkWeight() == null ? 1.0 : chunk.chunkWeight();
    return new ProfileSearchChunkResponse(
        chunk.id(),
        title,
        content,
        score,
        chunk.sourceType(),
        chunk.chunkType(),
        null,
        null,
        null,
        weight,
        null
    );
  }

  private List<ChunkDraft> buildChunkDrafts(UserProfile profile) {
    return List.of(
        draft("目标岗位", profile.targetRoles(), "TARGET", 1.0),
        draft("目标城市", profile.preferredCities(), "TARGET", 1.0),
        draft("技能栈", profile.skills(), "SKILL", 1.2),
        draft("项目经历", profile.projects(), "PROJECT", 1.3),
        draft("工作经历", profile.experience(), "EXPERIENCE", 1.2),
        draft("教育背景", profile.education(), "EDUCATION", 0.8),
        draft("简历原文", profile.resumeText(), "RESUME", 1.0),
        draft("正向关键词", profile.positiveKeywords(), "KEYWORD", 0.8),
        draft("负向关键词", profile.negativeKeywords(), "KEYWORD", 0.8),
        draft("硬性排除关键词", profile.hardRejectKeywords(), "KEYWORD", 0.8),
        draft("出勤偏好", profile.schedulePreference(), "TARGET", 1.0),
        draft("补充说明", profile.manualText(), "RESUME", 1.0)
    );
  }

  private ChunkDraft draft(String title, String content, String type, double weight) {
    String metadata = "{\"type\":\"" + type + "\",\"source\":\"resume\",\"weight\":" + weight + "}";
    return new ChunkDraft(title, content, "manual_profile", type, weight, metadata);
  }

  private List<ChunkDraft> buildHistoryChunkDrafts() {
    return jobHistoryRepository.findRecentForRag(50).stream()
        .filter(record -> !hasFeedback(record))
        .map(record -> new ChunkDraft(historyChunkTitle(record), historyChunkContent(record),
            historySourceType(record), "EXPERIENCE", 1.2,
            "{\"type\":\"EXPERIENCE\",\"source\":\"history\",\"weight\":1.2}"))
        .toList();
  }

  private List<ChunkDraft> buildFeedbackChunkDrafts() {
    return jobHistoryRepository.findRecentForRag(50).stream()
        .filter(this::hasFeedback)
        .map(feedbackChunkBuilder::build)
        .map(chunk -> new ChunkDraft(
            chunk.title(),
            chunk.content(),
            chunk.sourceType(),
            chunk.chunkType(),
            chunk.chunkWeight(),
            chunk.metadataJson()))
        .toList();
  }

  private boolean hasFeedback(JobHistoryRecord record) {
    return record != null
        && (!normalize(record.feedbackNote()).isBlank()
        || !normalize(record.applyStatus()).isBlank()
        || !normalize(record.chatStatus()).isBlank()
        || !normalize(record.interviewStatus()).isBlank());
  }

  private String historyChunkTitle(JobHistoryRecord record) {
    String cleanedCompanyName = cleanedHistoryCompanyName(record);
    String cleanedJobTitle = cleanedHistoryJobTitle(record);
    return "历史投递反馈 - %s - %s".formatted(
        cleanedCompanyName,
        cleanedJobTitle
    );
  }

  private String historyChunkContent(JobHistoryRecord record) {
    String cleanedCompanyName = cleanedHistoryCompanyName(record);
    String cleanedJobTitle = cleanedHistoryJobTitle(record);
    return """
        公司：%s
        岗位：%s
        城市：%s
        薪资：%s
        出勤周期：%s / %s
        AI 判断：%s
        AI 分数：%s
        方向：%s
        投递状态：%s
        沟通状态：%s
        面试状态：%s
        用户备注：%s
        主要风险：%s
        简历匹配点：%s
        """.formatted(
        cleanedCompanyName,
        cleanedJobTitle,
        fallback(record.city(), "未记录"),
        fallback(record.salary(), "未记录"),
        fallback(record.schedule(), "未记录"),
        fallback(record.duration(), "未记录"),
        fallback(record.aiDecision(), "未记录"),
        record.aiScore() == null ? "未记录" : record.aiScore(),
        fallback(record.aiDirection(), "未记录"),
        fallback(record.applyStatus(), "未记录"),
        fallback(record.chatStatus(), "未记录"),
        fallback(record.interviewStatus(), "未记录"),
        fallback(record.feedbackNote(), "无"),
        String.join("；", record.risks() == null ? List.of() : record.risks()),
        String.join("；", record.resumeMatches() == null ? List.of() : record.resumeMatches())
    ).trim();
  }

  private String cleanedHistoryCompanyName(JobHistoryRecord record) {
    String cleaned = jobFieldSanitizer.sanitizeCompanyName(record.companyName());
    return normalize(cleaned).isBlank() ? "未识别公司" : cleaned;
  }

  private String cleanedHistoryJobTitle(JobHistoryRecord record) {
    String cleaned = jobFieldSanitizer.sanitizeJobTitle(record.jobTitle());
    return normalize(cleaned).isBlank() ? "未识别岗位" : cleaned;
  }

  private String historySourceType(JobHistoryRecord record) {
    return normalize(record.feedbackNote()).isBlank()
        && normalize(record.applyStatus()).isBlank()
        && normalize(record.chatStatus()).isBlank()
        && normalize(record.interviewStatus()).isBlank()
        ? "job_history"
        : "feedback_history";
  }

  private String buildRawText(List<ChunkDraft> chunks) {
    StringBuilder builder = new StringBuilder();
    chunks.forEach(chunk -> {
      String normalized = normalize(chunk.content());
      if (!normalized.isBlank()) {
        builder.append("【").append(chunk.title()).append("】\n");
        builder.append(normalized).append("\n\n");
      }
    });
    return builder.toString().trim();
  }

  private List<String> tokenize(String query) {
    List<String> terms = new ArrayList<>();
    for (String term : Pattern.compile("[\\s,，、;；|/\\\\]+").split(query)) {
      String normalized = normalize(term);
      if (!normalized.isBlank() && normalized.length() >= 2 && !terms.contains(normalized)) {
        terms.add(normalized);
      }
    }
    return terms;
  }

  private int normalizeTopK(Integer topK) {
    if (topK == null) {
      return DEFAULT_TOP_K;
    }
    return Math.max(1, Math.min(MAX_TOP_K, topK));
  }

  private int scoreHint(String title) {
    if ("项目经历".equals(title) || "技能栈".equals(title)) {
      return 1;
    }
    return 0;
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  private String fallback(String value, String fallback) {
    String normalized = normalize(value);
    return normalized.isBlank() ? fallback : normalized;
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder();
      for (byte b : hash) {
        builder.append(String.format("%02x", b));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  private record ChunkDraft(
      String title,
      String content,
      String sourceType,
      String chunkType,
      double chunkWeight,
      String metadataJson
  ) {
  }
}
