package com.lt.aijobscreeningagent.service.analysis;

import com.lt.aijobscreeningagent.dto.JobAnalyzeRequest;
import org.springframework.stereotype.Service;

@Service
public class JobAnalysisPromptBuilder {

  public JobAnalysisPrompt build(
      JobAnalyzeRequest request,
      RuleAnalysisResult ruleAnalysis,
      RagContext ragContext
  ) {
    return new JobAnalysisPrompt(request, ruleAnalysis, ragContext);
  }
}
