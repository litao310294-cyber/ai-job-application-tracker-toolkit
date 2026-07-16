package com.lt.aijobscreeningagent.dto;

public record JobAnalyzeProfileRagChunk(
    Long id,
    String title,
    String content,
    int score,
    String sourceType,
    String chunkType,
    Double semanticScore,
    Double keywordScore,
    Double finalScore,
    Double chunkWeight,
    Double baseScore
) {
  public JobAnalyzeProfileRagChunk(
      Long id, String title, String content, int score, String sourceType,
      String chunkType, Double semanticScore, Double keywordScore, Double finalScore
  ) {
    this(id, title, content, score, sourceType, chunkType, semanticScore, keywordScore,
        finalScore, null, finalScore);
  }

  public JobAnalyzeProfileRagChunk(
      Long id, String title, String content, int score, String sourceType
  ) {
    this(id, title, content, score, sourceType, null, null, null, score / 100d, null, score / 100d);
  }
}
