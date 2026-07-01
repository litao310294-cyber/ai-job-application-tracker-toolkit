package com.lt.aijobscreeningagent.dto;

public record JobAnalyzeRequest(
    String jobTitle,
    String companyName,
    String salary,
    String city,
    String schedule,
    String duration,
    String jobText,
    Integer ruleScore,
    String ruleConclusion
) {
}
