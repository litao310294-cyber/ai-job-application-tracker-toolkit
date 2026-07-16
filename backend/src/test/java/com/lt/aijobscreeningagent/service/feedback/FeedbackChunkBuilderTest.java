package com.lt.aijobscreeningagent.service.feedback;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lt.aijobscreeningagent.dto.JobFeedbackResponse;
import com.lt.aijobscreeningagent.dto.StructuredJobInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeedbackChunkBuilderTest {

  @Test
  void buildsTypedFeedbackMemoryWithoutInventingMissingFields() {
    FeedbackChunkBuilder builder = new FeedbackChunkBuilder(new ObjectMapper());
    StructuredJobInfo job = new StructuredJobInfo(
        "AI应用开发实习生", "示例公司", "300/天", "北京", "本科", "不限",
        List.of("Java", "RAG"), List.of("实习"), "完整 JD", "VUE");
    JobFeedbackResponse feedback = new JobFeedbackResponse(
        7L, 11L, "已投递", "已沟通", "进入面试", "Java 与 RAG 项目匹配", null, null);

    FeedbackChunkBuilder.FeedbackChunk chunk = builder.build(job, feedback);

    assertTrue(chunk.content().contains("AI应用开发实习生"));
    assertTrue(chunk.content().contains("进入面试"));
    assertTrue(chunk.content().contains("Java 与 RAG 项目匹配"));
    assertTrue(chunk.content().contains("用户历史投递反馈"));
    assertTrue(chunk.metadataJson().contains("FEEDBACK"));
    assertTrue(chunk.metadataJson().contains("0.7"));
  }
}
