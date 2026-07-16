package com.lt.aijobscreeningagent.service.rag;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lt.aijobscreeningagent.dto.JobAnalyzeRequest;
import com.lt.aijobscreeningagent.dto.StructuredJobInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobQueryBuilderTest {

  private final JobQueryBuilder builder = new JobQueryBuilder();

  @Test
  void requestQueryContainsJobSemanticsAndTechnicalTerms() {
    String query = builder.build(new JobAnalyzeRequest(
        "AI应用开发实习生", "示例公司", "300元/天", "北京", "5天/周", "6个月",
        "负责构建RAG智能应用和Agent服务。要求熟悉Java、Spring Boot、Redis和Embedding。",
        80, "可投", null));

    assertTrue(query.contains("AI应用开发实习生"));
    assertTrue(query.contains("RAG智能应用"));
    assertTrue(query.contains("Java"));
    assertTrue(query.contains("Spring Boot"));
    assertTrue(query.contains("Redis"));
    assertTrue(query.contains("Embedding"));
  }

  @Test
  void incompleteCapturedRecordUsesRequestJdAsFallback() {
    StructuredJobInfo stored = new StructuredJobInfo(
        "", "示例公司", "", "北京", "", "", List.of(), List.of(), "", "VUE");
    JobAnalyzeRequest request = new JobAnalyzeRequest(
        "Java后端实习生", "示例公司", "200元/天", "北京", "5天/周", "3个月",
        "使用Spring Boot、MySQL和Redis开发后端服务。", null, null, 1L);

    String query = builder.build(stored, request);

    assertTrue(query.contains("Java后端实习生"));
    assertTrue(query.contains("Spring Boot"));
    assertTrue(query.contains("MySQL"));
    assertTrue(query.contains("Redis"));
  }
}
