package com.lt.aijobscreeningagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "embedding")
public class EmbeddingProperties {

  private boolean enabled = true;
  private String provider = "dashscope";
  private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
  private String apiKey = "";
  private String model = "text-embedding-v4";
  private int dimension = 1024;
  private int timeoutSeconds = 30;
  private int batchSize = 10;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public int getDimension() {
    return dimension;
  }

  public void setDimension(int dimension) {
    this.dimension = dimension;
  }

  public int getTimeoutSeconds() {
    return timeoutSeconds;
  }

  public void setTimeoutSeconds(int timeoutSeconds) {
    this.timeoutSeconds = timeoutSeconds;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  public boolean hasApiKey() {
    return apiKey != null && !apiKey.isBlank();
  }

  public boolean isUsable() {
    return enabled && hasApiKey();
  }
}
