package com.lt.aijobscreeningagent.profile;

public record ProfileSearchChunkResponse(
    Long id,
    String title,
    String content,
    int score,
    String sourceType,
    String chunkType,
    Double semanticScore,
    Double keywordScore,
    Double finalScore
) {

  public ProfileSearchChunkResponse(
      Long id,
      String title,
      String content,
      int score,
      String sourceType
  ) {
    this(id, title, content, score, sourceType, null, null, null, score / 100d);
  }
}
