package com.lt.aijobscreeningagent.profile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lt.aijobscreeningagent.config.EmbeddingProperties;
import com.lt.aijobscreeningagent.service.embedding.EmbeddingException;
import com.lt.aijobscreeningagent.service.embedding.EmbeddingService;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class VectorSearchService {

  private static final Logger log = LoggerFactory.getLogger(VectorSearchService.class);

  private final EmbeddingProperties properties;
  private final EmbeddingService embeddingService;
  private final UserProfileRagRepository repository;
  private final ObjectMapper objectMapper;

  public VectorSearchService(
      EmbeddingProperties properties,
      EmbeddingService embeddingService,
      UserProfileRagRepository repository,
      ObjectMapper objectMapper
  ) {
    this.properties = properties;
    this.embeddingService = embeddingService;
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  public List<ProfileSearchChunkResponse> search(String profileName, String query, int topK) {
    if (!properties.isUsable()) {
      throw new EmbeddingException("Embedding is disabled or API key is missing");
    }
    log.info("profile embedding query: {}", abbreviate(query, 1000));
    List<Float> queryVector = embeddingService.embed(query);
    List<ScoredChunk> scored = repository.findChunksByProfileName(profileName).stream()
        .filter(this::hasUsableEmbedding)
        .map(chunk -> score(chunk, queryVector))
        .filter(item -> item != null)
        .sorted(Comparator.comparingDouble(ScoredChunk::similarity).reversed()
            .thenComparing(item -> item.chunk().id()))
        .limit(Math.max(1, topK))
        .toList();
    if (scored.isEmpty()) {
      throw new EmbeddingException("No valid profile embeddings are available");
    }
    return scored.stream().map(item -> new ProfileSearchChunkResponse(
        item.chunk().id(),
        item.chunk().title(),
      item.chunk().content(),
        (int) Math.round(item.semanticScore() * 100),
        item.chunk().sourceType(),
        item.chunk().chunkType(),
        item.semanticScore(),
        null,
        item.semanticScore(),
        item.chunk().chunkWeight() == null ? 1.0d : item.chunk().chunkWeight(),
        item.semanticScore()
    )).toList();
  }

  private boolean hasUsableEmbedding(UserProfileChunk chunk) {
    return "SUCCESS".equalsIgnoreCase(chunk.embeddingStatus())
        && properties.getModel().equals(chunk.embeddingModel())
        && chunk.embeddingDimension() != null
        && chunk.embeddingDimension() == properties.getDimension()
        && chunk.embeddingJson() != null
        && !chunk.embeddingJson().isBlank();
  }

  private ScoredChunk score(UserProfileChunk chunk, List<Float> queryVector) {
    try {
      List<Float> vector = objectMapper.readValue(chunk.embeddingJson(), new TypeReference<>() {});
      if (vector == null || vector.size() != queryVector.size()) {
        return null;
      }
      double similarity = cosine(queryVector, vector);
      return Double.isFinite(similarity) ? new ScoredChunk(chunk, similarity) : null;
    } catch (Exception e) {
      return null;
    }
  }

  static double cosine(List<Float> left, List<Float> right) {
    if (left == null || right == null || left.size() != right.size() || left.isEmpty()) {
      return 0d;
    }
    double dot = 0d;
    double leftNorm = 0d;
    double rightNorm = 0d;
    for (int i = 0; i < left.size(); i++) {
      double a = left.get(i);
      double b = right.get(i);
      dot += a * b;
      leftNorm += a * a;
      rightNorm += b * b;
    }
    if (leftNorm == 0d || rightNorm == 0d) {
      return 0d;
    }
    return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
  }

  private String abbreviate(String value, int maxLength) {
    String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
    return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
  }

  private record ScoredChunk(UserProfileChunk chunk, double similarity) {

    private double semanticScore() {
      return Math.max(0d, Math.min(1d, (similarity + 1d) / 2d));
    }
  }
}
