package com.lt.aijobscreeningagent.llm;

import com.lt.aijobscreeningagent.dto.JobAnalyzeRequest;
import com.lt.aijobscreeningagent.dto.LlmAnalyzeResult;

public interface LlmClient {

  LlmAnalyzeResult analyze(JobAnalyzeRequest request);

  LlmAnalyzeResult analyze(JobAnalyzeRequest request, String profileContext);

  String generateJson(String systemPrompt, String userPrompt, int maxTokens);
}
