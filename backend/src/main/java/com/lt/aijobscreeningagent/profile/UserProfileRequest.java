package com.lt.aijobscreeningagent.profile;

public record UserProfileRequest(
    String targetRoles,
    String preferredCities,
    String skills,
    String projects,
    String positiveKeywords,
    String negativeKeywords,
    String hardRejectKeywords,
    String schedulePreference,
    String manualText
) {
}
