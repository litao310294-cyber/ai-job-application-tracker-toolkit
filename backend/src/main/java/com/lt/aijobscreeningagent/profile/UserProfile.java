package com.lt.aijobscreeningagent.profile;

import java.time.LocalDateTime;

public record UserProfile(
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
}
