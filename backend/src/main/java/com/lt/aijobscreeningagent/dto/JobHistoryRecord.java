package com.lt.aijobscreeningagent.dto;

import java.time.LocalDateTime;
import java.util.List;

public record JobHistoryRecord(
    Long jobRecordId,
    String companyName,
    String jobTitle,
    String city,
    String salary,
    String schedule,
    String duration,
    Integer ruleScore,
    String ruleConclusion,
    String aiDecision,
    Integer aiScore,
    String aiDirection,
    List<String> reasons,
    List<String> risks,
    List<String> resumeMatches,
    List<String> interviewFocus,
    String suggestedMessage,
    String applyStatus,
    String chatStatus,
    String interviewStatus,
    String feedbackNote,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
