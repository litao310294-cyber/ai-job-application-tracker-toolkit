package com.lt.aijobscreeningagent.dto;

public record JobFeedbackRequest(
    Long jobRecordId,
    String taskId,
    String applyStatus,
    String chatStatus,
    String interviewStatus,
    String feedbackNote,
    String rejectReason
) {
}
