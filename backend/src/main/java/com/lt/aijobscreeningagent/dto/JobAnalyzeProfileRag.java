package com.lt.aijobscreeningagent.dto;

import java.util.List;

public record JobAnalyzeProfileRag(
    boolean enabled,
    String profileVersion,
    String query,
    int chunkCount,
    List<JobAnalyzeProfileRagChunk> chunks,
    String reason,
    String retrievalMode
) {
  public JobAnalyzeProfileRag(
      boolean enabled,
      String profileVersion,
      String query,
      int chunkCount,
      List<JobAnalyzeProfileRagChunk> chunks,
      String reason
  ) {
    this(enabled, profileVersion, query, chunkCount, chunks, reason, null);
  }
}
