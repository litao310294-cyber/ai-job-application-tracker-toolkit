package com.lt.aijobscreeningagent.dto;

import java.time.LocalDateTime;

public record JobRecordSummary(
    Long jobRecordId,
    String jobTitle,
    String companyName,
    String salary,
    String city,
    Integer ruleScore,
    String ruleConclusion,
    String aiDecision,
    Integer aiScore,
    String aiDirection,
    String status,
    LocalDateTime createdAt
) {
}
