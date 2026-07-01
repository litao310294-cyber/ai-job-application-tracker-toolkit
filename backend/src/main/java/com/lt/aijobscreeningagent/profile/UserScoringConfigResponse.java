package com.lt.aijobscreeningagent.profile;

import java.time.LocalDateTime;

public record UserScoringConfigResponse(
    boolean exists,
    String status,
    Long id,
    String profileName,
    String configJson,
    String generatedBy,
    boolean confirmed,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
  public static UserScoringConfigResponse empty() {
    return new UserScoringConfigResponse(
        false,
        "missing",
        null,
        "default",
        "",
        "",
        false,
        null,
        null
    );
  }

  public static UserScoringConfigResponse from(UserScoringConfig config, String status) {
    return new UserScoringConfigResponse(
        true,
        status,
        config.id(),
        config.profileName(),
        config.configJson(),
        config.generatedBy(),
        config.confirmed(),
        config.createdAt(),
        config.updatedAt()
    );
  }
}
