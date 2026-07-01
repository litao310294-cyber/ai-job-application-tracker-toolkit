package com.lt.aijobscreeningagent.dto;

public record JobFeedbackRequest(
    Long jobRecordId,
    String applyStatus,
    String chatStatus,
    String interviewStatus,
    String feedbackNote,
    String rejectReason
) {
}
