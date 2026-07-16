package com.lt.aijobscreeningagent.dto;

public record JobCaptureResponse(
    boolean success,
    long jobRecordId,
    boolean created,
    int completenessScore,
    String extractionMode
) {
}
