package com.lt.aijobscreeningagent.profile;

public record ProfileSearchChunkResponse(
    Long id,
    String title,
    String content,
    int score,
    String sourceType
) {
}
