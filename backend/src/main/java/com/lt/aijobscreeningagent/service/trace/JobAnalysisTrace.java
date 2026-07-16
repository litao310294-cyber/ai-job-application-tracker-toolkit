package com.lt.aijobscreeningagent.service.trace;

import java.time.LocalDateTime;

public record JobAnalysisTrace(
    Long id,
    String taskId,
    Long jobRecordId,
    String stage,
    String inputData,
    String outputData,
    Long latencyMs,
    LocalDateTime createdTime
) {
}
