package com.lt.aijobscreeningagent.profile;

import java.time.LocalDateTime;

public record UserScoringConfig(
    Long id,
    String profileName,
    String configJson,
    String generatedBy,
    boolean confirmed,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
