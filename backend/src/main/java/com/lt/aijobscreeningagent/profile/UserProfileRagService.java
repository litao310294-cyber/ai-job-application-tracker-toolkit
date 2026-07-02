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

  public UserProfileRagService(
      UserProfileRepository userProfileRepository,
      UserProfileRagRepository userProfileRagRepository,
      JobHistoryRepository jobHistoryRepository,
      JobFieldSanitizer jobFieldSanitizer
  ) {
    this.userProfileRepository = userProfileRepository;
    this.userProfileRagRepository = userProfileRagRepository;
    this.jobHistoryRepository = jobHistoryRepository;
    this.jobFieldSanitizer = jobFieldSanitizer;
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

    List<ChunkDraft> chunks = buildChunkDrafts(profile);
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
          chunk.sourceType()
      );
      index++;
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
    List<UserProfileChunk> indexedChunks = userProfileRagRepository.findChunksByProfileName(profileName);
    return indexedChunks.stream()
        .map(chunk -> toSearchResponse(chunk, terms))
        .filter(chunk -> chunk.score() > 0)
        .sorted(Comparator.comparingInt(ProfileSearchChunkResponse::score).reversed()
            .thenComparing(ProfileSearchChunkResponse::id))
        .limit(limit)
        .toList();
  }

  public String profileVersion(String profileName) {
    return userProfileRagRepository.findLatestProfileVersion(profileName)
        .orElse("no-profile-index");
  }

  private ProfileSearchChunkResponse toSearchResponse(UserProfileChunk chunk, List<String> terms) {
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
    }
    if (score > 0 && chunk.scoreHint() != null) {
      score += chunk.scoreHint();
    }

    return new ProfileSearchChunkResponse(
        chunk.id(),
        title,
        content,
        score,
        chunk.sourceType()
    );
  }

  private Map<String, String> buildChunkMap(UserProfile profile) {
    Map<String, String> chunks = new LinkedHashMap<>();
    chunks.put("目标岗位", profile.targetRoles());
    chunks.put("目标城市", profile.preferredCities());
    chunks.put("技能栈", profile.skills());
    chunks.put("项目经历", profile.projects());
    chunks.put("正向关键词", profile.positiveKeywords());
    chunks.put("负向关键词", profile.negativeKeywords());
    chunks.put("硬性排除关键词", profile.hardRejectKeywords());
    chunks.put("出勤偏好", profile.schedulePreference());
    chunks.put("补充说明", profile.manualText());
    return chunks;
  }

  private List<ChunkDraft> buildChunkDrafts(UserProfile profile) {
    return buildChunkMap(profile).entrySet().stream()
        .map(entry -> new ChunkDraft(entry.getKey(), entry.getValue(), "manual_profile"))
        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
  }

  private List<ChunkDraft> buildHistoryChunkDrafts() {
    return jobHistoryRepository.findRecentForRag(50).stream()
        .map(record -> new ChunkDraft(historyChunkTitle(record), historyChunkContent(record), historySourceType(record)))
        .toList();
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
      String sourceType
  ) {
  }
}
