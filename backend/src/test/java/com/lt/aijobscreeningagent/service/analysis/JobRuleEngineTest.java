package com.lt.aijobscreeningagent.service.analysis;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lt.aijobscreeningagent.dto.JobAnalyzeRequest;
import org.junit.jupiter.api.Test;

class JobRuleEngineTest {

  @Test
  void preservesHardRejectConclusionForNonTargetRole() {
    JobAnalyzeRequest request = new JobAnalyzeRequest(
        "自动化测试工程师", "示例公司", "10K", "北京", "5天/周", "6个月",
        "负责测试开发", 20, "不投", null);

    RuleAnalysisResult result = new JobRuleEngine().evaluate(request);

    assertTrue(result.hardRejected());
    assertTrue(result.score() < 50);
  }
}
