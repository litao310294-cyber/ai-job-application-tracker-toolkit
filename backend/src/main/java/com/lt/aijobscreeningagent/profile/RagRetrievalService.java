package com.lt.aijobscreeningagent.profile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RagRetrievalService {

  private static final Logger log = LoggerFactory.getLogger(RagRetrievalService.class);
  private static final int CANDIDATE_TOP_K = 10;

  private final UserProfileRagService keywordService;
  private final VectorSearchService vectorSearchService;

  public RagRetrievalService(
      UserProfileRagService keywordService,
      VectorSearchService vectorSearchService
  ) {
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
      log.info("profile retrieval mode=VECTOR vectorCount={}", vector.size());
      return new RagRetrievalResult("VECTOR", limit(vector, topK));
    }

    int minKeywordScore = keyword.stream().mapToInt(ProfileSearchChunkResponse::score).min().orElse(0);
    int maxKeywordScore = keyword.stream().mapToInt(ProfileSearchChunkResponse::score).max().orElse(0);
    Map<Long, Candidate> merged = new HashMap<>();
    for (ProfileSearchChunkResponse item : vector) {
      merged.put(item.id(), new Candidate(item, value(item.semanticScore(), item.score() / 100d), 0d));
    }
    for (ProfileSearchChunkResponse item : keyword) {
      Candidate current = merged.get(item.id());
      double keywordScore = normalizeKeyword(item.score(), minKeywordScore, maxKeywordScore);
      if (current == null) {
        merged.put(item.id(), new Candidate(item, 0d, keywordScore));
      } else {
        merged.put(item.id(), new Candidate(current.item(), current.semanticScore(), keywordScore));
      }
    }
    List<ProfileSearchChunkResponse> result = merged.values().stream()
        .map(candidate -> new ProfileSearchChunkResponse(
            candidate.item().id(),
            candidate.item().title(),
            candidate.item().content(),
            (int) Math.round((0.7d * candidate.semanticScore() + 0.3d * candidate.keywordScore()) * 100),
            candidate.item().sourceType(),
            candidate.item().chunkType(),
            candidate.semanticScore(),
            candidate.keywordScore(),
            0.7d * candidate.semanticScore() + 0.3d * candidate.keywordScore()
        ))
        .sorted(Comparator.comparingInt(ProfileSearchChunkResponse::score).reversed()
            .thenComparing(ProfileSearchChunkResponse::id))
        .limit(Math.max(1, topK))
        .toList();
    log.info("profile retrieval mode=HYBRID vectorCount={} keywordCount={} resultCount={}",
        vector.size(), keyword.size(), result.size());
    result.forEach(item -> log.info("profile retrieval chunk id={} type={} semanticScore={} keywordScore={} finalScore={}",
        item.id(), item.chunkType(), item.semanticScore(), item.keywordScore(), item.finalScore()));
    return new RagRetrievalResult("HYBRID", result);
  }

  private double normalizeKeyword(int score, int min, int max) {
    if (max == min) {
      return score > 0 ? 1d : 0d;
    }
    return Math.max(0d, Math.min(1d, (score - min) / (double) (max - min)));
  }

  private double value(Double value, double fallback) {
    return value == null ? fallback : Math.max(0d, Math.min(1d, value));
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
    return values.stream().map(item -> {
      double normalized = normalizeKeyword(item.score(), min, max);
      return new ProfileSearchChunkResponse(
          item.id(), item.title(), item.content(), item.score(), item.sourceType(), item.chunkType(),
          null, normalized, normalized);
    }).toList();
  }

  private record Candidate(ProfileSearchChunkResponse item, double semanticScore, double keywordScore) {
  }

  public record RagRetrievalResult(String mode, List<ProfileSearchChunkResponse> chunks) {
  }
}
