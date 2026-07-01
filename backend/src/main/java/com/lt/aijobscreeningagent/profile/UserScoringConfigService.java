package com.lt.aijobscreeningagent.profile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lt.aijobscreeningagent.config.LlmProperties;
import com.lt.aijobscreeningagent.llm.LlmClient;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserScoringConfigService {

  private static final List<String> REQUIRED_FIELDS = List.of(
      "targetRoles",
      "preferredCities",
      "positiveKeywords",
      "negativeKeywords",
      "hardRejectKeywords",
      "scheduleRiskKeywords",
      "roleWeights",
      "skillWeights",
      "riskWeights"
  );

  private final UserProfileRepository userProfileRepository;
  private final UserScoringConfigRepository userScoringConfigRepository;
  private final LlmProperties llmProperties;
  private final LlmClient llmClient;
  private final ObjectMapper objectMapper;

  public UserScoringConfigService(
      UserProfileRepository userProfileRepository,
      UserScoringConfigRepository userScoringConfigRepository,
      LlmProperties llmProperties,
      LlmClient llmClient,
      ObjectMapper objectMapper
  ) {
    this.userProfileRepository = userProfileRepository;
    this.userScoringConfigRepository = userScoringConfigRepository;
    this.llmProperties = llmProperties;
    this.llmClient = llmClient;
    this.objectMapper = objectMapper;
  }

  public UserScoringConfigResponse generateDefaultConfig() {
    UserProfile profile = userProfileRepository.findDefault()
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Default user profile is not initialized"
        ));

    String status = "success";
    String generatedBy = "ai";
    String configJson;

    if (!llmProperties.isEnabled() || !llmProperties.hasApiKey()) {
      status = "fallback";
      generatedBy = "fallback";
      configJson = fallbackConfigJson(profile);
    } else {
      try {
        String rawJson = llmClient.generateJson(systemPrompt(), userPrompt(profile), 1500);
        configJson = validateAndNormalize(rawJson);
      } catch (RuntimeException e) {
        status = "fallback";
        generatedBy = "fallback";
        configJson = fallbackConfigJson(profile);
      }
    }

    UserScoringConfig saved = userScoringConfigRepository.saveDefault(configJson, generatedBy, false);
    return UserScoringConfigResponse.from(saved, status);
  }

  public UserScoringConfigResponse getDefaultConfig() {
    return userScoringConfigRepository.findDefault()
        .map(config -> UserScoringConfigResponse.from(config, "success"))
        .orElseGet(UserScoringConfigResponse::empty);
  }

  public UserScoringConfigResponse confirmDefaultConfig() {
    UserScoringConfig config = userScoringConfigRepository.confirmDefault()
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Default scoring config is not generated"
        ));
    return UserScoringConfigResponse.from(config, "success");
  }

  private String validateAndNormalize(String rawJson) {
    try {
      JsonNode root = objectMapper.readTree(rawJson);
      if (!root.isObject()) {
        throw new IllegalStateException("Scoring config must be a JSON object");
      }

      for (String field : REQUIRED_FIELDS) {
        if (!root.has(field)) {
          throw new IllegalStateException("Missing scoring config field: " + field);
        }
      }

      validateArray(root, "targetRoles", 50);
      validateArray(root, "preferredCities", 50);
      validateArray(root, "positiveKeywords", 50);
      validateArray(root, "negativeKeywords", 50);
      validateArray(root, "hardRejectKeywords", 50);
      validateArray(root, "scheduleRiskKeywords", 50);
      validateWeights(root, "roleWeights", 0, 50);
      validateWeights(root, "skillWeights", 0, 20);
      validateWeights(root, "riskWeights", -100, 0);

      return objectMapper.writeValueAsString(root);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Scoring config JSON parse failed", e);
    }
  }

  private void validateArray(JsonNode root, String field, int maxSize) {
    JsonNode node = root.path(field);
    if (!node.isArray()) {
      throw new IllegalStateException(field + " must be an array");
    }
    if (node.size() > maxSize) {
      throw new IllegalStateException(field + " is too large");
    }
    for (JsonNode item : node) {
      if (!item.isTextual()) {
        throw new IllegalStateException(field + " items must be strings");
      }
    }
  }

  private void validateWeights(JsonNode root, String field, int min, int max) {
    JsonNode node = root.path(field);
    if (!node.isObject()) {
      throw new IllegalStateException(field + " must be an object");
    }
    if (node.size() > 50) {
      throw new IllegalStateException(field + " is too large");
    }
    node.fields().forEachRemaining(entry -> {
      JsonNode value = entry.getValue();
      if (!value.isNumber()) {
        throw new IllegalStateException(field + " values must be numbers");
      }
      int weight = value.asInt();
      if (weight < min || weight > max) {
        throw new IllegalStateException(field + " value is out of range: " + entry.getKey());
      }
    });
  }

  private String fallbackConfigJson(UserProfile profile) {
    Map<String, Object> config = new LinkedHashMap<>();
    List<String> targetRoles = splitText(profile.targetRoles());
    List<String> preferredCities = splitText(profile.preferredCities());
    List<String> positiveKeywords = splitText(profile.positiveKeywords());
    if (positiveKeywords.isEmpty()) {
      positiveKeywords = splitText(profile.skills());
    }
    List<String> negativeKeywords = splitText(profile.negativeKeywords());
    List<String> hardRejectKeywords = splitText(profile.hardRejectKeywords());
    List<String> scheduleRiskKeywords = List.of("6天/周", "7天/周", "12个月", "一年");

    config.put("targetRoles", targetRoles);
    config.put("preferredCities", preferredCities);
    config.put("positiveKeywords", positiveKeywords);
    config.put("negativeKeywords", negativeKeywords);
    config.put("hardRejectKeywords", hardRejectKeywords);
    config.put("scheduleRiskKeywords", scheduleRiskKeywords);
    config.put("roleWeights", buildWeightMap(targetRoles, 30));
    config.put("skillWeights", buildWeightMap(positiveKeywords, 10));
    config.put("riskWeights", buildWeightMap(negativeKeywords, -30));

    try {
      return objectMapper.writeValueAsString(config);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Fallback scoring config serialization failed", e);
    }
  }

  private Map<String, Integer> buildWeightMap(List<String> keywords, int weight) {
    Map<String, Integer> weights = new LinkedHashMap<>();
    keywords.stream()
        .limit(50)
        .forEach(keyword -> weights.put(keyword, weight));
    return weights;
  }

  private List<String> splitText(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    return Arrays.stream(text.split("[,，、\\n;；]+"))
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .distinct()
        .limit(50)
        .toList();
  }

  private String systemPrompt() {
    return """
        你是一个岗位评分配置生成助手。
        你的任务是根据用户的求职画像，生成一份结构化岗位评分配置。
        这份配置会被后续规则评分模块读取，所以必须稳定、可解释、字段固定。

        只输出合法 JSON，不要 Markdown，不要代码块，不要解释文字。
        不要生成 JavaScript 代码，不要输出正则表达式执行代码。

        JSON 结构必须严格为：
        {
          "targetRoles": [],
          "preferredCities": [],
          "positiveKeywords": [],
          "negativeKeywords": [],
          "hardRejectKeywords": [],
          "scheduleRiskKeywords": [],
          "roleWeights": {},
          "skillWeights": {},
          "riskWeights": {}
        }

        约束：
        - targetRoles、preferredCities、positiveKeywords、negativeKeywords、hardRejectKeywords、scheduleRiskKeywords 都必须是字符串数组。
        - roleWeights 的权重必须是 0 到 50 的整数。
        - skillWeights 的权重必须是 0 到 20 的整数。
        - riskWeights 的权重必须是 -100 到 0 的整数。
        - 每类关键词最多 50 个。
        """;
  }

  private String userPrompt(UserProfile profile) {
    return """
        请根据以下默认用户画像生成岗位评分配置：

        目标岗位方向：%s
        目标城市：%s
        技能栈：%s
        项目经历：%s
        正向关键词：%s
        负向关键词：%s
        硬性排除关键词：%s
        出勤与周期偏好：%s
        补充说明：%s
        """.formatted(
        valueOrEmpty(profile.targetRoles()),
        valueOrEmpty(profile.preferredCities()),
        valueOrEmpty(profile.skills()),
        valueOrEmpty(profile.projects()),
        valueOrEmpty(profile.positiveKeywords()),
        valueOrEmpty(profile.negativeKeywords()),
        valueOrEmpty(profile.hardRejectKeywords()),
        valueOrEmpty(profile.schedulePreference()),
        valueOrEmpty(profile.manualText())
    );
  }

  private String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }
}
