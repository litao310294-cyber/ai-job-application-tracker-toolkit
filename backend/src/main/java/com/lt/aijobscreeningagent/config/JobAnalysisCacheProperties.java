package com.lt.aijobscreeningagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "job-analysis.cache")
public class JobAnalysisCacheProperties {

  private boolean enabled = true;
  private long ttlHours = 72;
  private String keyPrefix = "ai-job-agent:analysis:";

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public long getTtlHours() {
    return ttlHours;
  }

  public void setTtlHours(long ttlHours) {
    this.ttlHours = ttlHours;
  }

  public String getKeyPrefix() {
    return keyPrefix;
  }

  public void setKeyPrefix(String keyPrefix) {
    this.keyPrefix = keyPrefix;
  }
}
