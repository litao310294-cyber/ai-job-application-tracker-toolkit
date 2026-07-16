package com.lt.aijobscreeningagent.service.analysis;

import com.lt.aijobscreeningagent.dto.JobAnalyzeRequest;

public record JobAnalysisPrompt(
    JobAnalyzeRequest request,
    RuleAnalysisResult ruleAnalysis,
    RagContext ragContext
) {
}
