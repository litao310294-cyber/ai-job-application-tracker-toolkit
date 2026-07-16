package com.lt.aijobscreeningagent.service.analysis;

import com.lt.aijobscreeningagent.dto.LlmAnalyzeResult;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ResultValidator {

  public LlmAnalyzeResult validate(LlmAnalyzeResult result) {
    if (result == null || result.score() == null || result.score() < 0 || result.score() > 100
        || result.direction() == null || result.direction().isBlank()
        || result.reasons() == null || result.risks() == null
        || result.resumeMatches() == null || result.interviewFocus() == null
        || result.suggestedMessage() == null) {
      throw new IllegalStateException("LLM analysis result failed validation");
    }
    return result;
  }
}
