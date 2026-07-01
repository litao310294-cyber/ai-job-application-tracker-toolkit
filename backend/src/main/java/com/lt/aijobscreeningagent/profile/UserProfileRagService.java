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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserProfileRagService {

  private static final int DEFAULT_TOP_K = 5;
  private static final int MAX_TOP_K = 20;

  private final UserProfileRepository userProfileRepository;
  private final UserProfileRagRepository userProfileRagRepository;

  public UserProfileRagService(
      UserProfileRepository userProfileRepository,
      UserProfileRagRepository userProfileRagRepository
  ) {
    this.userProfileRepository = userProfileRepository;
    this.userProfileRagRepository = userProfileRagRepository;
  }

  public ProfileReindexResponse reindexDefaultProfile() {
    UserProfile profile = userProfileRepository.findDefault()
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Default user profile is not initialized"
        ));

    Map<String, String> chunks = buildChunkMap(profile);
    String rawText = buildRawText(chunks);

    userProfileRagRepository.deleteDefaultProfileIndex();
    Long documentId = userProfileRagRepository.saveDocument(rawText, sha256(rawText));

    int index = 0;
    for (Map.Entry<String, String> entry : chunks.entrySet()) {
      String content = normalize(entry.getValue());
      if (content.isBlank()) {
        continue;
      }
      userProfileRagRepository.saveChunk(
          documentId,
          index,
          entry.getKey(),
          content,
          sha256(entry.getKey() + "\n" + content),
          scoreHint(entry.getKey())
      );
      index++;
    }

    return new ProfileReindexResponse(true, profile.profileName(), documentId, index);
  }

  public ProfileSearchResponse searchDefaultProfile(String query, Integer topK) {
    int limit = normalizeTopK(topK);
    String normalizedQuery = normalize(query);
    if (normalizedQuery.isBlank()) {
      return new ProfileSearchResponse(false, "", limit, List.of());
    }

    List<String> terms = tokenize(normalizedQuery);
    List<UserProfileChunk> indexedChunks = userProfileRagRepository.findDefaultChunks();
    List<ProfileSearchChunkResponse> chunks = indexedChunks.stream()
        .map(chunk -> toSearchResponse(chunk, terms))
        .filter(chunk -> chunk.score() > 0)
        .sorted(Comparator.comparingInt(ProfileSearchChunkResponse::score).reversed()
            .thenComparing(ProfileSearchChunkResponse::id))
        .limit(limit)
        .toList();

    return new ProfileSearchResponse(!indexedChunks.isEmpty(), normalizedQuery, limit, chunks);
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

  private String buildRawText(Map<String, String> chunks) {
    StringBuilder builder = new StringBuilder();
    chunks.forEach((title, content) -> {
      String normalized = normalize(content);
      if (!normalized.isBlank()) {
        builder.append("【").append(title).append("】\n");
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
}
