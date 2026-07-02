package com.lt.aijobscreeningagent.dto;

public record JobAnalyzeProfileRagChunk(
    Long id,
    String title,
    String content,
    int score,
    String sourceType
) {
}
