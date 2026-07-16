package com.lt.aijobscreeningagent.service.analysis;

import com.lt.aijobscreeningagent.dto.JobAnalyzeRequest;
import org.springframework.stereotype.Service;

/** Keeps the existing client-side rule result stable while providing a backend rule boundary. */
@Service
public class JobRuleEngine {

  public RuleAnalysisResult evaluate(JobAnalyzeRequest request) {
    Integer score = request == null ? null : request.ruleScore();
    String conclusion = request == null ? "" : request.ruleConclusion();
    if (score == null) {
      score = 72;
    }
    if (conclusion == null || conclusion.isBlank()) {
      conclusion = "可投";
    }
    boolean hardRejected = conclusion.contains("不投")
        || conclusion.contains("涓嶆姇")
        || conclusion.toLowerCase().contains("reject");
    return new RuleAnalysisResult(score, conclusion, hardRejected, "existing-rule-result");
  }
}
