package com.lt.aijobscreeningagent.service.rag;

import com.lt.aijobscreeningagent.dto.JobAnalyzeRequest;
import com.lt.aijobscreeningagent.dto.StructuredJobInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class JobQueryBuilder {

  public String build(StructuredJobInfo job) {
    if (job == null) {
      return "";
    }
    String rawJd = rawValue(job.rawJD());
    return format(
        job.jobTitle(), job.city(), job.education(), job.experience(),
        String.join("、", job.skills()), String.join("、", job.jobTags()),
        extractSection(rawJd, "职责", "工作内容"), extractSection(rawJd, "要求", "任职资格"),
        technicalText(rawJd)
    );
  }

  public String build(JobAnalyzeRequest request) {
    if (request == null) {
      return "";
    }
    String rawJd = rawValue(request.jobText());
    return format(
        request.jobTitle(), request.city(), "", "",
        "", "", extractSection(rawJd, "职责", "工作内容"),
        extractSection(rawJd, "要求", "任职资格"), technicalText(rawJd)
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
    add(lines, "技术相关内容：", technicalText);
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
        + ")\\s*[:：]?\\s*(.*?)(?=\\n\\s*(?:岗位)?(?:职责|工作内容|要求|任职资格|任职要求|加分项|福利)\\s*[:：]?|$)")
        .matcher(normalized);
    return matcher.find() ? matcher.group(1).trim() : "";
  }

  private String technicalText(String rawJd) {
    String normalized = value(rawJd);
    if (normalized.isBlank()) {
      return "";
    }
    StringBuilder result = new StringBuilder();
    for (String token : List.of("Java", "Spring", "Spring Boot", "SpringCloud", "MySQL",
        "Redis", "Python", "RAG", "Embedding", "Agent", "DeepSeek", "LangChain",
        "Linux", "Git", "向量数据库", "大模型")) {
      if (normalized.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT))) {
        if (!result.isEmpty()) {
          result.append("、");
        }
        result.append(token);
      }
    }
    return result.toString();
  }

  private String value(String value) {
    return value == null ? "" : value.replaceAll("\\s+", " ").trim();
  }

  private String rawValue(String value) {
    return value == null ? "" : value.replace("\r", "").trim();
  }
}
