package com.lt.aijobscreeningagent.llm;

import com.lt.aijobscreeningagent.dto.JobAnalyzeRequest;
import com.lt.aijobscreeningagent.dto.LlmAnalyzeResult;

public interface LlmClient {

  LlmAnalyzeResult analyze(JobAnalyzeRequest request);
}
