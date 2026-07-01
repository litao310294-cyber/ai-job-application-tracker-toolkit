package com.lt.aijobscreeningagent.profile;

import java.time.LocalDateTime;

public record UserProfileResponse(
    boolean initialized,
    Long id,
    String profileName,
    String targetRoles,
    String preferredCities,
    String skills,
    String projects,
    String positiveKeywords,
    String negativeKeywords,
    String hardRejectKeywords,
    String schedulePreference,
    String manualText,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
  public static UserProfileResponse empty() {
    return new UserProfileResponse(
        false,
        null,
        "default",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        null,
        null
    );
  }

  public static UserProfileResponse from(UserProfile profile) {
    return new UserProfileResponse(
        true,
        profile.id(),
        profile.profileName(),
        profile.targetRoles(),
        profile.preferredCities(),
        profile.skills(),
        profile.projects(),
        profile.positiveKeywords(),
        profile.negativeKeywords(),
        profile.hardRejectKeywords(),
        profile.schedulePreference(),
        profile.manualText(),
        profile.createdAt(),
        profile.updatedAt()
    );
  }
}
