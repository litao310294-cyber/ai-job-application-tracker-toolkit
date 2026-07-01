package com.lt.aijobscreeningagent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lt.aijobscreeningagent.config.JobAnalysisCacheProperties;
import com.lt.aijobscreeningagent.dto.JobAnalyzeRequest;
import com.lt.aijobscreeningagent.dto.JobAnalyzeResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class JobAnalysisCacheService {

  private static final Logger log = LoggerFactory.getLogger(JobAnalysisCacheService.class);

  private final JobAnalysisCacheProperties properties;
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  public JobAnalysisCacheService(
      JobAnalysisCacheProperties properties,
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper
  ) {
    this.properties = properties;
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
  }

  public String buildCacheKey(JobAnalyzeRequest request) {
    return properties.getKeyPrefix() + sha256(String.join("\n",
        normalize(request.jobTitle()),
        normalize(request.companyName()),
        normalize(request.city()),
        normalize(request.salary()),
        normalize(request.schedule()),
        normalize(request.duration()),
        normalize(request.jobText()),
        request.ruleScore() == null ? "" : String.valueOf(request.ruleScore()),
        normalize(request.ruleConclusion())
    ));
  }

  public Optional<JobAnalyzeResponse> get(String cacheKey) {
    if (!properties.isEnabled()) {
      return Optional.empty();
    }

    try {
      String value = redisTemplate.opsForValue().get(cacheKey);
      if (value == null || value.isBlank()) {
        return Optional.empty();
      }
      return Optional.of(objectMapper.readValue(value, JobAnalyzeResponse.class));
    } catch (RedisConnectionFailureException e) {
      log.warn("Redis cache unavailable, continue without cache.");
      return Optional.empty();
    } catch (RuntimeException | JsonProcessingException e) {
      log.warn("Failed to read job analysis cache, continue without cache.");
      return Optional.empty();
    }
  }

  public void put(String cacheKey, JobAnalyzeResponse response) {
    if (!properties.isEnabled()) {
      return;
    }

    try {
      String value = objectMapper.writeValueAsString(response);
      long ttlHours = Math.max(1, properties.getTtlHours());
      redisTemplate.opsForValue().set(cacheKey, value, Duration.ofHours(ttlHours));
    } catch (RedisConnectionFailureException e) {
      log.warn("Redis cache unavailable, skip writing cache.");
    } catch (RuntimeException | JsonProcessingException e) {
      log.warn("Failed to write job analysis cache, skip writing cache.");
    }
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
