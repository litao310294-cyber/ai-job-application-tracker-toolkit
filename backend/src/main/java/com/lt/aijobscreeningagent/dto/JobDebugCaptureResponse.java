package com.lt.aijobscreeningagent.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record JobDebugCaptureResponse(
    boolean success,
    String receivedAt,
    String message,
    JsonNode structuredJobInfo,
    JsonNode bossHelperJobData
) {}
