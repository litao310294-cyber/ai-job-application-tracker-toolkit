package com.lt.aijobscreeningagent.service.analysis;

import com.lt.aijobscreeningagent.config.LlmProperties;
import com.lt.aijobscreeningagent.dto.LlmAnalyzeResult;
import com.lt.aijobscreeningagent.llm.LlmClient;
import org.springframework.stereotype.Service;

@Service
public class LlmAnalyzer {

  private final LlmProperties properties;
  private final LlmClient llmClient;

  public LlmAnalyzer(LlmProperties properties, LlmClient llmClient) {
    this.properties = properties;
    this.llmClient = llmClient;
  }

  public boolean enabled() {
    return properties.isEnabled() && properties.hasApiKey();
  }

  public String model() {
    return properties.getModel() == null ? "" : properties.getModel();
  }

  public LlmAnalyzeResult analyze(JobAnalysisPrompt prompt) {
    return llmClient.analyze(prompt.request(), prompt.ragContext().text());
  }
}
