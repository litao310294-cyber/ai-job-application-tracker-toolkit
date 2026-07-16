package com.lt.aijobscreeningagent.service.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lt.aijobscreeningagent.config.EmbeddingProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class DashScopeEmbeddingService implements EmbeddingService {

  private static final Logger log = LoggerFactory.getLogger(DashScopeEmbeddingService.class);

  private final EmbeddingProperties properties;
  private final ObjectMapper objectMapper;
  private final RestClient restClient;

  public DashScopeEmbeddingService(EmbeddingProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;

    HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
        .build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())));
    this.restClient = RestClient.builder()
        .baseUrl(trimTrailingSlash(properties.getBaseUrl()))
        .requestFactory(requestFactory)
        .build();
  }

  @Override
  public List<Float> embed(String text) {
    List<List<Float>> vectors = embedBatch(List.of(text == null ? "" : text));
    if (vectors.size() != 1) {
      throw new EmbeddingException("DashScope returned an unexpected embedding count");
    }
    return vectors.get(0);
  }

  @Override
  public List<List<Float>> embedBatch(List<String> texts) {
    if (!properties.isUsable()) {
      throw new EmbeddingException("Embedding is disabled or DASHSCOPE_API_KEY is empty");
    }
    if (texts == null || texts.isEmpty()) {
      return List.of();
    }

    Map<String, Object> body = new HashMap<>();
    body.put("model", properties.getModel());
    body.put("input", texts);
    body.put("dimensions", properties.getDimension());

    for (int index = 0; index < texts.size(); index++) {
      String text = texts.get(index) == null ? "" : texts.get(index);
      String preview = text.substring(0, Math.min(100, text.length()));
      log.info("DashScope embedding request. index={}, textLength={}, textPreview={}, model={}",
          index, text.length(), preview, properties.getModel());
    }

    try {
      JsonNode response = restClient.post()
          .uri("/embeddings")
          .contentType(MediaType.APPLICATION_JSON)
          .header("Authorization", "Bearer " + properties.getApiKey())
          .body(body)
          .retrieve()
          .body(JsonNode.class);
      return parseVectors(response, texts.size());
    } catch (RestClientResponseException e) {
      log.error(
          "DashScope embedding request failed. exceptionMessage={}, responseBody={}, httpStatus={}",
          e.getMessage(), e.getResponseBodyAsString(), e.getStatusCode().value(), e);
      throw new EmbeddingException(
          "DashScope embedding request failed: HTTP " + e.getStatusCode().value(), e);
    } catch (RuntimeException e) {
      log.error(
          "DashScope embedding request failed. exceptionMessage={}, responseBody={}, httpStatus={}",
          e.getMessage(), "", "N/A", e);
      if (e instanceof EmbeddingException embeddingException) {
        throw embeddingException;
      }
      throw new EmbeddingException("DashScope embedding request failed", e);
    }
  }

  private List<List<Float>> parseVectors(JsonNode response, int expectedCount) {
    if (response == null) {
      throw new EmbeddingException("DashScope embedding response is empty");
    }

    JsonNode data = response.path("data");
    if (!data.isArray() || data.isEmpty()) {
      data = response.path("output").path("embeddings");
    }
    if (!data.isArray() || data.size() != expectedCount) {
      throw new EmbeddingException("DashScope embedding response count is invalid");
    }

    Map<Integer, List<Float>> indexed = new HashMap<>();
    for (int position = 0; position < data.size(); position++) {
      JsonNode item = data.get(position);
      int index = item.has("index") ? item.path("index").asInt(position)
          : item.has("text_index") ? item.path("text_index").asInt(position) : position;
      indexed.put(index, parseVector(item.path("embedding")));
    }

    List<List<Float>> vectors = new ArrayList<>(expectedCount);
    for (int index = 0; index < expectedCount; index++) {
      List<Float> vector = indexed.get(index);
      if (vector == null) {
        throw new EmbeddingException("DashScope embedding index is missing: " + index);
      }
      vectors.add(vector);
    }
    return vectors;
  }

  private List<Float> parseVector(JsonNode vectorNode) {
    if (!vectorNode.isArray() || vectorNode.isEmpty()) {
      throw new EmbeddingException("DashScope returned an empty embedding vector");
    }
    List<Float> vector = new ArrayList<>(vectorNode.size());
    vectorNode.forEach(value -> vector.add((float) value.asDouble()));
    if (properties.getDimension() > 0 && vector.size() != properties.getDimension()) {
      throw new EmbeddingException(
          "Embedding dimension mismatch: expected " + properties.getDimension()
              + ", actual " + vector.size());
    }
    return List.copyOf(vector);
  }

  private String trimTrailingSlash(String value) {
    if (value == null || value.isBlank()) {
      return "https://dashscope.aliyuncs.com/compatible-mode/v1";
    }
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }
}
