package com.lt.aijobscreeningagent.profile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Combines semantic and lexical profile evidence without an LLM reranker. */
@Service
public class RagRetrievalService {

  private static final Logger log = LoggerFactory.getLogger(RagRetrievalService.class);
  private static final int CANDIDATE_TOP_K = 10;
  private static final double SEMANTIC_WEIGHT = 0.7d;
  private static final double KEYWORD_WEIGHT = 0.3d;
  private static final Map<String, Double> DEFAULT_CHUNK_WEIGHTS = Map.of(
      "PROJECT", 1.3d,
      "EXPERIENCE", 1.2d,
      "SKILL", 1.2d,
      "TARGET", 1.0d,
      "RESUME", 1.0d,
      "EDUCATION", 0.8d,
      "KEYWORD", 0.8d,
      "FEEDBACK", 0.7d
  );

  private final UserProfileRagService keywordService;
  private final VectorSearchService vectorSearchService;

  public RagRetrievalService(UserProfileRagService keywordService, VectorSearchService vectorSearchService) {
    this.keywordService = keywordService;
    this.vectorSearchService = vectorSearchService;
  }

  public List<ProfileSearchChunkResponse> retrieve(String profileName, String query, int topK) {
    return retrieveWithTrace(profileName, query, topK).chunks();
  }

  public RagRetrievalResult retrieveWithTrace(String profileName, String query, int topK) {
    List<ProfileSearchChunkResponse> keyword = safeKeyword(profileName, query);
    List<ProfileSearchChunkResponse> vector;
    try {
      vector = vectorSearchService.search(profileName, query, CANDIDATE_TOP_K);
    } catch (RuntimeException e) {
      log.info("profile retrieval mode=FALLBACK_KEYWORD reason={}", e.getMessage());
      return new RagRetrievalResult("FALLBACK_KEYWORD", limit(normalizeKeywordTrace(keyword), topK));
    }
    if (vector.isEmpty()) {
      log.info("profile retrieval mode=FALLBACK_KEYWORD reason=no-vector-results");
      return new RagRetrievalResult("FALLBACK_KEYWORD", limit(normalizeKeywordTrace(keyword), topK));
    }
    if (keyword.isEmpty()) {
      List<ProfileSearchChunkResponse> result = limit(scoreVectorTrace(vector), topK);
      log.info("profile retrieval mode=VECTOR vectorCount={}", vector.size());
      return new RagRetrievalResult("VECTOR", result);
    }

    int minKeywordScore = keyword.stream().mapToInt(ProfileSearchChunkResponse::score).min().orElse(0);
    int maxKeywordScore = keyword.stream().mapToInt(ProfileSearchChunkResponse::score).max().orElse(0);
    Map<Long, Candidate> merged = new HashMap<>();
    for (ProfileSearchChunkResponse item : vector) {
      merged.put(item.id(), new Candidate(item, value(item.semanticScore(), item.score() / 100d), 0d));
    }
    for (ProfileSearchChunkResponse item : keyword) {
      double keywordScore = normalizeKeyword(item.score(), minKeywordScore, maxKeywordScore);
      Candidate current = merged.get(item.id());
      if (current == null) {
        merged.put(item.id(), new Candidate(item, 0d, keywordScore));
      } else {
        merged.put(item.id(), new Candidate(preferMetadata(current.item(), item), current.semanticScore(), keywordScore));
      }
    }

    List<ProfileSearchChunkResponse> result = merged.values().stream()
        .map(candidate -> scoreCandidate(candidate.item(), candidate.semanticScore(), candidate.keywordScore(), true))
        .sorted(Comparator.comparingDouble(ProfileSearchChunkResponse::finalScore).reversed()
            .thenComparing(ProfileSearchChunkResponse::id))
        .limit(Math.max(1, topK))
        .toList();
    log.info("profile retrieval mode=HYBRID vectorCount={} keywordCount={} resultCount={}",
        vector.size(), keyword.size(), result.size());
    result.forEach(item -> log.info(
        "profile retrieval chunk id={} type={} semanticScore={} keywordScore={} chunkWeight={} baseScore={} finalScore={}",
        item.id(), item.chunkType(), item.semanticScore(), item.keywordScore(), item.chunkWeight(),
        item.baseScore(), item.finalScore()));
    return new RagRetrievalResult("HYBRID", result);
  }

  private ProfileSearchChunkResponse scoreCandidate(
      ProfileSearchChunkResponse item, double semanticScore, double keywordScore, boolean hybrid) {
    double normalizedSemantic = clamp(semanticScore);
    double normalizedKeyword = clamp(keywordScore);
    double baseScore = hybrid
        ? SEMANTIC_WEIGHT * normalizedSemantic + KEYWORD_WEIGHT * normalizedKeyword
        : Math.max(normalizedSemantic, normalizedKeyword);
    double chunkWeight = chunkWeight(item);
    double finalScore = baseScore * chunkWeight;
    return new ProfileSearchChunkResponse(
        item.id(), item.title(), item.content(), (int) Math.round(finalScore * 100), item.sourceType(),
        item.chunkType(), normalizedSemantic, normalizedKeyword, finalScore, chunkWeight, baseScore);
  }

  private List<ProfileSearchChunkResponse> scoreVectorTrace(List<ProfileSearchChunkResponse> values) {
    return values.stream()
        .map(item -> scoreCandidate(item, value(item.semanticScore(), item.score() / 100d), 0d, false))
        .sorted(Comparator.comparingDouble(ProfileSearchChunkResponse::finalScore).reversed()
            .thenComparing(ProfileSearchChunkResponse::id))
        .toList();
  }

  private double normalizeKeyword(int score, int min, int max) {
    if (max == min) {
      return score > 0 ? 1d : 0d;
    }
    return clamp((score - min) / (double) (max - min));
  }

  private double value(Double value, double fallback) {
    return value == null ? clamp(fallback) : clamp(value);
  }

  private double clamp(double value) {
    return Math.max(0d, Math.min(1d, value));
  }

  private double chunkWeight(ProfileSearchChunkResponse item) {
    if (item.chunkWeight() != null && item.chunkWeight() > 0d) {
      return item.chunkWeight();
    }
    return DEFAULT_CHUNK_WEIGHTS.getOrDefault(
        item.chunkType() == null ? "" : item.chunkType().toUpperCase(), 1.0d);
  }

  private ProfileSearchChunkResponse preferMetadata(
      ProfileSearchChunkResponse semanticItem, ProfileSearchChunkResponse keywordItem) {
    if (keywordItem.chunkWeight() != null) {
      return new ProfileSearchChunkResponse(
          semanticItem.id(), semanticItem.title(), semanticItem.content(), semanticItem.score(),
          semanticItem.sourceType(), semanticItem.chunkType(), semanticItem.semanticScore(),
          semanticItem.keywordScore(), semanticItem.finalScore(), keywordItem.chunkWeight(),
          semanticItem.baseScore());
    }
    return semanticItem;
  }

  private List<ProfileSearchChunkResponse> safeKeyword(String profileName, String query) {
    try {
      return keywordService.searchProfileChunks(profileName, query, CANDIDATE_TOP_K);
    } catch (RuntimeException e) {
      log.warn("Keyword profile retrieval failed.", e);
      return new ArrayList<>();
    }
  }

  private List<ProfileSearchChunkResponse> limit(List<ProfileSearchChunkResponse> values, int topK) {
    return values.stream().limit(Math.max(1, topK)).toList();
  }

  private List<ProfileSearchChunkResponse> normalizeKeywordTrace(List<ProfileSearchChunkResponse> values) {
    if (values.isEmpty()) {
      return values;
    }
    int min = values.stream().mapToInt(ProfileSearchChunkResponse::score).min().orElse(0);
    int max = values.stream().mapToInt(ProfileSearchChunkResponse::score).max().orElse(0);
    return values.stream()
        .map(item -> scoreCandidate(item, 0d, normalizeKeyword(item.score(), min, max), false))
        .sorted(Comparator.comparingDouble(ProfileSearchChunkResponse::finalScore).reversed()
            .thenComparing(ProfileSearchChunkResponse::id))
        .toList();
  }

  private record Candidate(ProfileSearchChunkResponse item, double semanticScore, double keywordScore) {
  }

  public record RagRetrievalResult(String mode, List<ProfileSearchChunkResponse> chunks) {
  }
}
