package com.lt.aijobscreeningagent.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record JobDebugCaptureRequest(
    String sourcePlatform,
    String sourceUrl,
    String capturedAt,
    JsonNode structuredJobInfo,
    JsonNode bossHelperJobData
) {}
