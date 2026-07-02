package com.lt.aijobscreeningagent.profile;

import java.time.LocalDateTime;

public record UserProfileChunk(
    Long id,
    String profileName,
    Long documentId,
    Integer chunkIndex,
    String title,
    String content,
    String sourceType,
    String contentHash,
    Integer scoreHint,
    LocalDateTime createdAt
) {
}
