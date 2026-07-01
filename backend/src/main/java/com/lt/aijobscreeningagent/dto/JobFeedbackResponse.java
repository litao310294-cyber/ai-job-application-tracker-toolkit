package com.lt.aijobscreeningagent.dto;

import java.time.LocalDateTime;

public record JobFeedbackResponse(
    Long id,
    Long jobRecordId,
    String applyStatus,
    String chatStatus,
    String interviewStatus,
    String feedbackNote,
    String rejectReason,
    LocalDateTime createdAt
) {
}
