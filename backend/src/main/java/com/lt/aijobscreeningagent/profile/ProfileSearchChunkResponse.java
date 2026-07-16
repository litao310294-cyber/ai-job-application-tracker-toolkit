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
    Double finalScore,
    Double chunkWeight,
    Double baseScore
) {

  public ProfileSearchChunkResponse(
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
    this(id, title, content, score, sourceType, chunkType, semanticScore, keywordScore,
        finalScore, null, finalScore);
  }

  public ProfileSearchChunkResponse(
      Long id,
      String title,
      String content,
      int score,
      String sourceType
  ) {
    this(id, title, content, score, sourceType, null, null, null, score / 100d, null, score / 100d);
  }
}
