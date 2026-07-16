package com.lt.aijobscreeningagent.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lt.aijobscreeningagent.llm.LlmClient;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ResumeProfileLlmService {

  private final LlmClient llmClient;
  private final ObjectMapper objectMapper;

  public ResumeProfileLlmService(LlmClient llmClient, ObjectMapper objectMapper) {
    this.llmClient = llmClient;
    this.objectMapper = objectMapper;
  }

  public ResumeProfileExtraction extract(ResumeRawText rawText) {
    if (rawText == null || rawText.text() == null || rawText.text().isBlank()) {
      throw new IllegalArgumentException("Resume PDF contains no extractable text");
    }
    String systemPrompt = """
        你是简历结构化解析器。只输出合法 JSON，不要输出 Markdown 或解释。
        只能根据简历原文提取事实，不要编造信息。
        JSON 字段必须为：
        {
          "targetRoles": ["目标岗位"],
          "skills": ["技能"],
          "projects": ["项目经历摘要"],
          "experience": "工作或实习经历摘要",
          "education": "教育背景摘要",
          "keywords": ["求职关键词"]
        }
        没有内容时使用空数组或空字符串。
        """;
    String userPrompt = "简历原文：\n" + rawText.text();
    String json = llmClient.generateJson(systemPrompt, userPrompt, 1800);
    try {
      ResumeProfileExtraction result = objectMapper.readValue(json, ResumeProfileExtraction.class);
      return new ResumeProfileExtraction(
          clean(result.targetRoles()),
          clean(result.skills()),
          clean(result.projects()),
          normalize(result.experience()),
          normalize(result.education()),
          clean(result.keywords())
      );
    } catch (Exception e) {
      throw new IllegalStateException("Resume profile JSON parse failed", e);
    }
  }

  private List<String> clean(List<String> values) {
    if (values == null) {
      return List.of();
    }
    return values.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(String::trim)
        .distinct()
        .toList();
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim();
  }
}
