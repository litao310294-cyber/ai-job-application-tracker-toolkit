package com.lt.aijobscreeningagent.dto;

import java.util.List;

public record JobAnalyzeResponse(
    Long jobRecordId,
    String taskId,
    String status,
    String decision,
    Integer score,
    String direction,
    List<String> reasons,
    List<String> risks,
    List<String> resumeMatches,
    List<String> interviewFocus,
    String suggestedMessage
) {
}
