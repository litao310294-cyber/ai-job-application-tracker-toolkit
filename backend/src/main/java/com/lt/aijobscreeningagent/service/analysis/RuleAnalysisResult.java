package com.lt.aijobscreeningagent.service.analysis;

public record RuleAnalysisResult(
    Integer score,
    String conclusion,
    boolean hardRejected,
    String reason
) {
}
