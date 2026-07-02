package com.lt.aijobscreeningagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class JobFieldSanitizer {

  private static final Logger log = LoggerFactory.getLogger(JobFieldSanitizer.class);
  private static final String UNKNOWN_COMPANY = "未识别公司";
  private static final String UNKNOWN_JOB_TITLE = "未识别岗位";

  public String sanitizeCompanyName(String raw) {
    String value = sanitizeShortField(raw, 60);
    if (isBlank(value) || isDirtyCompanyName(value)) {
      if (!isBlank(raw)) {
        log.debug("Sanitized dirty companyName. raw={}", abbreviate(raw, 80));
      }
      return UNKNOWN_COMPANY;
    }
    return value;
  }

  public String sanitizeJobTitle(String raw) {
    String value = sanitizeShortField(raw, 80);
    if (isBlank(value)) {
      return UNKNOWN_JOB_TITLE;
    }
    if (isLikelyJdSentence(value)) {
      log.debug("Sanitized dirty jobTitle. raw={}", abbreviate(raw, 80));
      return UNKNOWN_JOB_TITLE;
    }
    return value;
  }

  public String sanitizeShortField(String raw, int maxLength) {
    if (raw == null) {
      return "";
    }
    String value = raw.replaceAll("\\s+", " ").trim();
    if (value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength).trim();
  }

  public boolean isUnknownCompany(String value) {
    return isBlank(value) || UNKNOWN_COMPANY.equals(value.trim());
  }

  public boolean isDirtyCompanyName(String raw) {
    String value = sanitizeShortField(raw, 80);
    if (isBlank(value)) {
      return true;
    }
    if (isPlaceholderCompanyName(value)) {
      return true;
    }
    if (isNumberedRequirementSentence(value)) {
      return true;
    }
    if (value.length() > 40) {
      return true;
    }
    if (isLikelyJdSentence(value)) {
      return true;
    }
    if (value.matches(".*(天/周|个月|本科|硕士|博士|经验不限|Java|后端开发|服务端开发|实习生).*")) {
      return true;
    }
    return value.matches("^(未识别|职位|岗位|学历|经验|薪资|地址|工作地址|立即沟通|收藏)$");
  }

  private boolean isPlaceholderCompanyName(String value) {
    return value.matches("^(公司|某公司|企业|招聘方|未命名公司)$");
  }

  private boolean isLikelyJdSentence(String value) {
    return value.matches(".*(岗位职责|职位描述|任职要求|负责|参与|熟悉|经验|接口开发|数据处理|问题排查|线上问题排查|缺陷修复|功能迭代|系统设计|模块设计|开发及交付|工作内容|任职资格|优先考虑|本科及以上学历|计算机/AI/软件工程).*");
  }

  private boolean isNumberedRequirementSentence(String value) {
    return value.matches("^\\s*[一二三四五六七八九十0-9]+[、.．)）\\s-].*")
        && (value.length() > 20 || isLikelyJdSentence(value));
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private String abbreviate(String value, int maxLength) {
    String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
    if (normalized.length() <= maxLength) {
      return normalized;
    }
    return normalized.substring(0, maxLength) + "...";
  }
}
