package com.lt.aijobscreeningagent.dto;

import java.util.List;

public record StructuredJobInfo(
    String jobTitle,
    String companyName,
    String salary,
    String city,
    String education,
    String experience,
    List<String> skills,
    List<String> jobTags,
    String rawJD,
    String extractionMode
) {
  public StructuredJobInfo {
    skills = skills == null ? List.of() : List.copyOf(skills);
    jobTags = jobTags == null ? List.of() : List.copyOf(jobTags);
    extractionMode = extractionMode == null || extractionMode.isBlank() ? "DOM" : extractionMode;
  }
}
