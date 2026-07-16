package com.lt.aijobscreeningagent.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lt.aijobscreeningagent.config.LlmProperties;
import com.lt.aijobscreeningagent.dto.JobAnalyzeRequest;
import com.lt.aijobscreeningagent.dto.LlmAnalyzeResult;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

  private static final List<String> VALID_DECISIONS = List.of("优先投", "可投", "谨慎投", "不投");

  private final LlmProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public OpenAiCompatibleLlmClient(LlmProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
        .build();
  }

  @Override
  public LlmAnalyzeResult analyze(JobAnalyzeRequest request) {
    return analyze(request, "");
  }

  @Override
  public LlmAnalyzeResult analyze(JobAnalyzeRequest request, String profileContext) {
    if (!properties.isEnabled() || !properties.hasApiKey()) {
      throw new IllegalStateException("LLM is disabled or API key is empty");
    }

    try {
      HttpRequest httpRequest = HttpRequest.newBuilder()
          .uri(URI.create(chatCompletionsUrl()))
          .timeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
          .header("Authorization", "Bearer " + properties.getApiKey())
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(
              buildRequestBody(systemPrompt(), userPrompt(request, profileContext), 1200)
          ))
          .build();

      HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException("LLM API returned HTTP " + response.statusCode());
      }

      String content = extractContent(response.body());
      LlmAnalyzeResult result = objectMapper.readValue(cleanJsonContent(content), LlmAnalyzeResult.class);
      validate(result);
      return result;
    } catch (IOException e) {
      throw new IllegalStateException("LLM response parse failed", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("LLM request interrupted", e);
    }
  }

  @Override
  public String generateJson(String systemPrompt, String userPrompt, int maxTokens) {
    if (!properties.isEnabled() || !properties.hasApiKey()) {
      throw new IllegalStateException("LLM is disabled or API key is empty");
    }

    try {
      HttpRequest httpRequest = HttpRequest.newBuilder()
          .uri(URI.create(chatCompletionsUrl()))
          .timeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
          .header("Authorization", "Bearer " + properties.getApiKey())
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(systemPrompt, userPrompt, maxTokens)))
          .build();

      HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException("LLM API returned HTTP " + response.statusCode());
      }

      return cleanJsonContent(extractContent(response.body()));
    } catch (IOException e) {
      throw new IllegalStateException("LLM response parse failed", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("LLM request interrupted", e);
    }
  }

  private String chatCompletionsUrl() {
    String baseUrl = properties.getBaseUrl();
    if (baseUrl.endsWith("/")) {
      baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
    }
    return baseUrl + "/chat/completions";
  }

  private String buildRequestBody(String systemPrompt, String userPrompt, int maxTokens)
      throws JsonProcessingException {
    Map<String, Object> body = Map.of(
        "model", properties.getModel(),
        "temperature", 0.2,
        "max_tokens", maxTokens,
        "messages", List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userPrompt)
        )
    );
    return objectMapper.writeValueAsString(body);
  }

  private String extractContent(String responseBody) throws JsonProcessingException {
    JsonNode root = objectMapper.readTree(responseBody);
    JsonNode content = root.path("choices").path(0).path("message").path("content");
    if (content.isMissingNode() || content.asText().isBlank()) {
      throw new IllegalStateException("LLM response content is empty");
    }
    return content.asText();
  }

  private String cleanJsonContent(String content) {
    String trimmed = content.trim();
    if (trimmed.startsWith("```")) {
      trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
      trimmed = trimmed.replaceFirst("\\s*```$", "");
    }
    return trimmed.trim();
  }

  private void validate(LlmAnalyzeResult result) {
    if (result == null) {
      throw new IllegalStateException("LLM result is null");
    }
    if (!VALID_DECISIONS.contains(result.decision())) {
      throw new IllegalStateException("LLM decision is invalid");
    }
    if (result.score() == null || result.score() < 0 || result.score() > 100) {
      throw new IllegalStateException("LLM score is invalid");
    }
    if (result.direction() == null || result.direction().isBlank()) {
      throw new IllegalStateException("LLM direction is empty");
    }
    if (result.reasons() == null || result.risks() == null
        || result.resumeMatches() == null || result.interviewFocus() == null) {
      throw new IllegalStateException("LLM list fields must not be null");
    }
    if (result.suggestedMessage() == null) {
      throw new IllegalStateException("LLM suggestedMessage must not be null");
    }
  }

  private String systemPrompt() {
    return """
        【用户能力画像】中的项目、技能、经历和简历内容是用户真实能力证据。
        【用户历史行为反馈】只作为辅助参考，不能覆盖或替代真实技能、项目经历，也不能覆盖规则层结论。
        如果没有【用户历史行为反馈】，不要编造任何投递、沟通或面试历史。
        你是一个个人求职跟进助手，只负责根据用户提供的岗位页面可见文本做本地化求职初筛建议。
        用户背景、技术栈、项目经历、目标方向、城市偏好和排斥方向必须以【用户能力画像】为准。
        如果【用户能力画像】没有提到某项技能、项目或经历，不要编造。
        如果没有检索到用户能力画像，请仅基于岗位信息、规则评分和规则结论做保守分析，并明确避免夸大用户匹配点。

        你必须只返回合法 JSON，不要 Markdown，不要代码块，不要输出 JSON 之外的任何文本。
        resumeMatches 必须优先基于【用户能力画像】中的项目、技能和偏好；如果资料没有提到某项经历，不要编造。
        risks 可以结合【用户能力画像】中的排斥方向、出勤要求和岗位要求判断；历史反馈只能作为辅助线索。
        suggestedMessage 要结合【用户能力画像】中的真实技能和项目；如果资料不足，请给出更通用、保守的中文开场白。

        JSON 字段固定为：
        {
          "decision": "优先投|可投|谨慎投|不投",
          "score": 0-100 的整数,
          "direction": "岗位方向",
          "reasons": ["原因1", "原因2"],
          "risks": ["风险1", "风险2"],
          "resumeMatches": ["匹配的项目或经历"],
          "interviewFocus": ["面试准备重点"],
          "suggestedMessage": "中文开场白或跟进建议"
        }
        decision 只能从 优先投、可投、谨慎投、不投 中选择。
        reasons、risks、resumeMatches、interviewFocus 必须是字符串数组。
        suggestedMessage 必须是中文字符串。
        """;
  }

  private String userPrompt(JobAnalyzeRequest request) {
    return userPrompt(request, "");
  }

  private String userPrompt(JobAnalyzeRequest request, String profileContext) {
    return """
        请分析以下岗位是否适合当前用户投递。
        岗位标题：%s
        公司：%s
        薪资：%s
        城市：%s
        出勤：%s
        周期：%s
        本地规则分数：%s
        本地规则结论：%s

        岗位文本：
        %s

        %s
        """.formatted(
        valueOrEmpty(request.jobTitle()),
        valueOrEmpty(request.companyName()),
        valueOrEmpty(request.salary()),
        valueOrEmpty(request.city()),
        valueOrEmpty(request.schedule()),
        valueOrEmpty(request.duration()),
        request.ruleScore() == null ? "未提供" : request.ruleScore(),
        valueOrEmpty(request.ruleConclusion()),
        valueOrEmpty(request.jobText()),
        valueOrEmpty(profileContext)
    );
  }

  private String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }
}
