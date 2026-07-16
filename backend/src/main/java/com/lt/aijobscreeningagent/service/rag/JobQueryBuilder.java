package com.lt.aijobscreeningagent.service.rag;

import com.lt.aijobscreeningagent.dto.JobAnalyzeRequest;
import com.lt.aijobscreeningagent.dto.StructuredJobInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Builds a job-only query for profile retrieval; user resume content is never added here. */
@Service
public class JobQueryBuilder {

  private static final int JD_SUMMARY_LIMIT = 800;
  private static final List<String> TECHNICAL_TERMS = List.of(
      "Java", "JavaScript", "TypeScript", "Spring", "Spring Boot", "SpringCloud", "Spring Cloud",
      "MySQL", "PostgreSQL", "Redis", "MongoDB", "Python", "Go", "Golang", "C++", "RAG",
      "Embedding", "Agent", "Tool Calling", "DeepSeek", "LangChain", "LlamaIndex", "Linux", "Git",
      "Docker", "Kubernetes", "Kafka", "RabbitMQ", "向量数据库", "大模型", "机器学习", "深度学习"
  );

  public String build(StructuredJobInfo job) {
    return build(job, null);
  }

  /** Merges a captured database record with the current request without losing a richer JD. */
  public String build(StructuredJobInfo job, JobAnalyzeRequest request) {
    if (job == null && request == null) {
      return "";
    }
    String rawJd = firstNonBlank(job == null ? null : job.rawJD(), request == null ? null : request.jobText());
    String title = firstNonBlank(job == null ? null : job.jobTitle(), request == null ? null : request.jobTitle());
    String city = firstNonBlank(job == null ? null : job.city(), request == null ? null : request.city());
    String education = job == null ? "" : job.education();
    String experience = job == null ? "" : job.experience();
    String skills = job == null ? "" : join(job.skills());
    String tags = job == null ? "" : join(job.jobTags());
    return format(
        title,
        city,
        education,
        experience,
        skills,
        tags,
        extractSection(rawJd, "职责", "工作内容", "工作职责"),
        extractSection(rawJd, "要求", "任职资格", "任职要求", "岗位要求"),
        summarize(rawJd),
        technicalText(rawJd, skills, title)
    );
  }

  public String build(JobAnalyzeRequest request) {
    if (request == null) {
      return "";
    }
    String rawJd = rawValue(request.jobText());
    return format(
        request.jobTitle(),
        request.city(),
        "",
        "",
        "",
        "",
        extractSection(rawJd, "职责", "工作内容", "工作职责"),
        extractSection(rawJd, "要求", "任职资格", "任职要求", "岗位要求"),
        summarize(rawJd),
        technicalText(rawJd, "", request.jobTitle())
    );
  }

  private String format(
      String title,
      String city,
      String education,
      String experience,
      String skills,
      String tags,
      String responsibilities,
      String requirements,
      String jdSummary,
      String technicalText
  ) {
    List<String> lines = new ArrayList<>();
    add(lines, "岗位方向：", title);
    add(lines, "工作城市：", city);
    add(lines, "学历要求：", education);
    add(lines, "经验要求：", experience);
    add(lines, "核心技能：", skills);
    add(lines, "岗位标签：", tags);
    add(lines, "岗位职责：", responsibilities);
    add(lines, "岗位要求：", requirements);
    add(lines, "JD文本摘要：", jdSummary);
    add(lines, "技术关键词：", technicalText);
    return String.join("\n", lines).trim();
  }

  private void add(List<String> lines, String label, String value) {
    String normalized = value(value);
    if (!normalized.isBlank()) {
      lines.add(label + "\n" + normalized);
    }
  }

  private String extractSection(String text, String... headings) {
    String normalized = rawValue(text);
    if (normalized.isBlank()) {
      return "";
    }
    String headingPattern = String.join("|", headings);
    var matcher = Pattern.compile("(?is)(?:^|\\n)\\s*(?:岗位)?(?:" + headingPattern
        + ")\\s*[:：]?\\s*(.*?)(?=\\n\\s*(?:岗位)?(?:职责|工作内容|工作职责|要求|任职资格|任职要求|岗位要求|加分项|福利)\\s*[:：]?|$)")
        .matcher(normalized);
    return matcher.find() ? matcher.group(1).trim() : "";
  }

  private String technicalText(String rawJd, String structuredSkills, String title) {
    String normalized = value(rawJd + " " + structuredSkills + " " + title);
    if (normalized.isBlank()) {
      return "";
    }
    StringBuilder result = new StringBuilder();
    for (String token : TECHNICAL_TERMS) {
      if (normalized.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT))) {
        if (!result.isEmpty()) {
          result.append("、");
        }
        result.append(token);
      }
    }
    return result.toString();
  }

  private String summarize(String rawJd) {
    String normalized = value(rawJd);
    if (normalized.length() <= JD_SUMMARY_LIMIT) {
      return normalized;
    }
    return normalized.substring(0, JD_SUMMARY_LIMIT) + "…";
  }

  private String join(List<String> values) {
    return values == null ? "" : String.join("、", values);
  }

  private String firstNonBlank(String first, String fallback) {
    return value(first).isBlank() ? value(fallback) : value(first);
  }

  private String value(String value) {
    return value == null ? "" : value.replaceAll("\\s+", " ").trim();
  }

  private String rawValue(String value) {
    return value == null ? "" : value.replace("\r", "").trim();
  }
}
