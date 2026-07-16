package com.lt.aijobscreeningagent.service.feedback;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lt.aijobscreeningagent.dto.JobFeedbackResponse;
import com.lt.aijobscreeningagent.dto.JobHistoryRecord;
import com.lt.aijobscreeningagent.dto.StructuredJobInfo;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Converts user feedback into a small, auditable profile-memory chunk. */
@Service
public class FeedbackChunkBuilder {

  public static final String CHUNK_TYPE = "FEEDBACK";
  public static final double CHUNK_WEIGHT = 0.7d;

  private final ObjectMapper objectMapper;

  public FeedbackChunkBuilder(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public FeedbackChunk build(StructuredJobInfo job, JobFeedbackResponse feedback) {
    String title = firstNonBlank(
        job == null ? null : job.jobTitle(),
        "岗位反馈 #" + value(feedback == null ? null : feedback.jobRecordId()));
    String content = buildFeedbackContent(
        title,
        job == null ? null : job.companyName(),
        job == null ? null : job.city(),
        feedback == null ? null : feedback.applyStatus(),
        feedback == null ? null : feedback.chatStatus(),
        feedback == null ? null : feedback.interviewStatus(),
        feedback == null ? null : feedback.feedbackNote(),
        feedback == null ? null : feedback.rejectReason());
    return new FeedbackChunk(
        "历史行为反馈 - " + title,
        content,
        "job_feedback",
        CHUNK_TYPE,
        CHUNK_WEIGHT,
        metadata(feedback == null ? null : feedback.id(), feedback == null ? null : feedback.jobRecordId()));
  }

  public FeedbackChunk build(JobHistoryRecord history) {
    String title = firstNonBlank(history == null ? null : history.jobTitle(), "历史反馈");
    String content = buildFeedbackContent(
        title,
        history == null ? null : history.companyName(),
        history == null ? null : history.city(),
        history == null ? null : history.applyStatus(),
        history == null ? null : history.chatStatus(),
        history == null ? null : history.interviewStatus(),
        history == null ? null : history.feedbackNote(),
        null);
    if (history != null && history.resumeMatches() != null && !history.resumeMatches().isEmpty()) {
      content += "\n主要匹配因素：\n" + String.join("、", history.resumeMatches());
    }
    if (history != null && history.risks() != null && !history.risks().isEmpty()) {
      content += "\n主要风险：\n" + String.join("、", history.risks());
    }
    return new FeedbackChunk(
        "历史行为反馈 - " + title,
        content,
        "job_feedback",
        CHUNK_TYPE,
        CHUNK_WEIGHT,
        metadata(null, history == null ? null : history.jobRecordId()));
  }

  public String buildFeedbackContent(
      String jobTitle,
      String companyName,
      String city,
      String applyStatus,
      String chatStatus,
      String interviewStatus,
      String feedbackNote,
      String rejectReason) {
    StringBuilder content = new StringBuilder("用户历史投递反馈：\n");
    append(content, "岗位方向", jobTitle);
    append(content, "公司", companyName);
    append(content, "城市", city);
    append(content, "投递结果", applyStatus);
    append(content, "沟通状态", chatStatus);
    append(content, "面试状态", interviewStatus);
    append(content, "反馈原因", feedbackNote);
    append(content, "拒绝原因", rejectReason);
    return content.toString().trim();
  }

  private String metadata(Long feedbackId, Long jobRecordId) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("type", CHUNK_TYPE);
    values.put("source", "job_feedback");
    values.put("weight", CHUNK_WEIGHT);
    if (feedbackId != null) {
      values.put("feedbackId", feedbackId);
    }
    if (jobRecordId != null) {
      values.put("jobRecordId", jobRecordId);
    }
    try {
      return objectMapper.writeValueAsString(values);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize feedback chunk metadata", e);
    }
  }

  private void append(StringBuilder builder, String label, String value) {
    if (value != null && !value.isBlank()) {
      builder.append(label).append("：").append(value.trim()).append("\n");
    }
  }

  private String firstNonBlank(String first, String fallback) {
    return first == null || first.isBlank() ? fallback : first.trim();
  }

  private String value(Object value) {
    return value == null ? "unknown" : String.valueOf(value);
  }

  public record FeedbackChunk(
      String title,
      String content,
      String sourceType,
      String chunkType,
      double chunkWeight,
      String metadataJson
  ) {
  }
}
