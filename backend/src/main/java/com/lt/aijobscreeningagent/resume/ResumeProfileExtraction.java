package com.lt.aijobscreeningagent.resume;

import java.util.List;

public record ResumeProfileExtraction(
    List<String> targetRoles,
    List<String> skills,
    List<String> projects,
    String experience,
    String education,
    List<String> keywords
) {

  public ResumeProfileExtraction {
    targetRoles = targetRoles == null ? List.of() : List.copyOf(targetRoles);
    skills = skills == null ? List.of() : List.copyOf(skills);
    projects = projects == null ? List.of() : List.copyOf(projects);
    keywords = keywords == null ? List.of() : List.copyOf(keywords);
  }
}
