package com.lt.aijobscreeningagent.profile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lt.aijobscreeningagent.config.EmbeddingProperties;
import com.lt.aijobscreeningagent.service.embedding.EmbeddingService;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ProfileEmbeddingIndexService {

  private static final Logger log = LoggerFactory.getLogger(ProfileEmbeddingIndexService.class);
  private static final String SUCCESS = "SUCCESS";

  private final EmbeddingProperties properties;
  private final EmbeddingService embeddingService;
  private final UserProfileRagRepository repository;
  private final ObjectMapper objectMapper;

  public ProfileEmbeddingIndexService(
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

  public void reindex(String profileName) {
    if (!properties.isUsable()) {
      log.info("Profile embedding index skipped: embedding disabled or API key missing.");
      return;
    }

    List<UserProfileChunk> chunks = repository.findChunksByProfileName(profileName);
    List<UserProfileChunk> pending = chunks.stream()
        .filter(chunk -> !isCurrent(chunk))
        .toList();
    int batchSize = Math.max(1, properties.getBatchSize());
    int successCount = 0;
    int failureCount = 0;
    for (int start = 0; start < pending.size(); start += batchSize) {
      List<UserProfileChunk> batch = pending.subList(start, Math.min(start + batchSize, pending.size()));
      try {
        List<List<Float>> vectors = embeddingService.embedBatch(
            batch.stream().map(UserProfileChunk::content).toList());
        if (vectors.size() != batch.size()) {
          throw new IllegalStateException("Embedding result count does not match chunk count");
        }
        for (int index = 0; index < batch.size(); index++) {
          UserProfileChunk chunk = batch.get(index);
          List<Float> vector = vectors.get(index);
          repository.updateEmbedding(
              chunk.id(),
              serialize(vector),
              properties.getModel(),
              vector.size(),
              SUCCESS,
              chunk.contentHash()
          );
          successCount++;
        }
      } catch (RuntimeException e) {
        failureCount += batch.size();
        for (UserProfileChunk chunk : batch) {
          try {
            repository.markEmbeddingFailed(
                chunk.id(), properties.getModel(), properties.getDimension(), chunk.contentHash());
          } catch (RuntimeException statusException) {
            log.debug("Could not persist embedding failure status for chunk {}", chunk.id(), statusException);
          }
        }
        log.warn("Profile embedding batch failed; keyword retrieval remains available. profileName={}, count={}",
            profileName, batch.size(), e);
      }
    }
    log.info("Profile embedding indexing finished. profileName={}, chunkCount={}, pendingCount={}, successCount={}, failureCount={}",
        profileName, chunks.size(), pending.size(), successCount, failureCount);
  }

  private boolean isCurrent(UserProfileChunk chunk) {
    return SUCCESS.equalsIgnoreCase(chunk.embeddingStatus())
        && properties.getModel().equals(chunk.embeddingModel())
        && chunk.embeddingDimension() != null
        && chunk.embeddingDimension() == properties.getDimension()
        && chunk.contentHash() != null
        && chunk.contentHash().equals(chunk.embeddingContentHash())
        && chunk.embeddingJson() != null
        && !chunk.embeddingJson().isBlank();
  }

  private String serialize(List<Float> vector) {
    try {
      return objectMapper.writeValueAsString(new ArrayList<>(vector));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize embedding vector", e);
    }
  }
}
