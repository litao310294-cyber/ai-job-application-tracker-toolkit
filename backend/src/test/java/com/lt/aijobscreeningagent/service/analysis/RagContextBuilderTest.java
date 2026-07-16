package com.lt.aijobscreeningagent.service.analysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lt.aijobscreeningagent.profile.ProfileSearchChunkResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagContextBuilderTest {

  @Test
  void separatesCapabilityAndFeedbackEvidenceAndSupportsNoFeedback() {
    RagContextBuilder builder = new RagContextBuilder();
    RagContext context = builder.build(List.of(
        new ProfileSearchChunkResponse(1L, "项目", "RAG 项目", 90, "resume", "PROJECT",
            .9, .8, .87, 1.3, .87),
        new ProfileSearchChunkResponse(2L, "历史反馈", "进入面试", 50, "job_feedback", "FEEDBACK",
            .5, .6, .53, .7, .53)));

    assertTrue(context.text().contains("【用户能力画像】"));
    assertTrue(context.text().contains("【用户历史行为反馈】"));
    assertTrue(context.text().contains("chunkWeight：0.7"));
    assertFalse(builder.build(List.of()).text().contains("FEEDBACK"));
  }
}
