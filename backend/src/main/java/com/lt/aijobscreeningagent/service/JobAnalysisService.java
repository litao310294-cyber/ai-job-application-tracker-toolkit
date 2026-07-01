package com.lt.aijobscreeningagent.service;

import com.lt.aijobscreeningagent.config.LlmProperties;
import com.lt.aijobscreeningagent.dto.JobAnalyzeRequest;
import com.lt.aijobscreeningagent.dto.JobAnalyzeResponse;
import com.lt.aijobscreeningagent.dto.JobRecordSummary;
import com.lt.aijobscreeningagent.dto.LlmAnalyzeResult;
import com.lt.aijobscreeningagent.llm.LlmClient;
import com.lt.aijobscreeningagent.repository.JobRecordRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class JobAnalysisService {

  private final LlmProperties llmProperties;
  private final LlmClient llmClient;
  private final JobRecordRepository jobRecordRepository;
  private final JobAnalysisCacheService jobAnalysisCacheService;

  public JobAnalysisService(
      LlmProperties llmProperties,
      LlmClient llmClient,
      JobRecordRepository jobRecordRepository,
      JobAnalysisCacheService jobAnalysisCacheService
  ) {
    this.llmProperties = llmProperties;
    this.llmClient = llmClient;
    this.jobRecordRepository = jobRecordRepository;
    this.jobAnalysisCacheService = jobAnalysisCacheService;
  }

  public JobAnalyzeResponse analyze(JobAnalyzeRequest request) {
    String cacheKey = jobAnalysisCacheService.buildCacheKey(request);
    var cachedResponse = jobAnalysisCacheService.get(cacheKey);
    if (cachedResponse.isPresent()) {
      return cachedResponse.get();
    }

    String taskId = UUID.randomUUID().toString();
    long jobRecordId = jobRecordRepository.saveJobRecord(request);
    JobAnalyzeResponse response;

    if (!llmProperties.isEnabled() || !llmProperties.hasApiKey()) {
      response = fallbackAnalyze(taskId, request);
      jobRecordRepository.saveJobAnalysis(jobRecordId, response);
      jobAnalysisCacheService.put(cacheKey, response);
      return response;
    }

    try {
      LlmAnalyzeResult result = llmClient.analyze(request);
      response = new JobAnalyzeResponse(
          taskId,
          "success",
          result.decision(),
          result.score(),
          result.direction(),
          result.reasons(),
          result.risks(),
          result.resumeMatches(),
          result.interviewFocus(),
          result.suggestedMessage()
      );
    } catch (RuntimeException e) {
      response = fallbackAnalyze(taskId, request);
    }

    jobRecordRepository.saveJobAnalysis(jobRecordId, response);
    jobAnalysisCacheService.put(cacheKey, response);
    return response;
  }

  public List<JobRecordSummary> findRecentRecords(Integer limit) {
    return jobRecordRepository.findRecentRecords(limit);
  }

  private JobAnalyzeResponse fallbackAnalyze(String taskId, JobAnalyzeRequest request) {
    int score = request.ruleScore() != null ? request.ruleScore() : 72;
    String decision = request.ruleConclusion() != null && !request.ruleConclusion().isBlank()
        ? request.ruleConclusion()
        : "可投";
    String direction = detectMockDirection(request);

    return new JobAnalyzeResponse(
        taskId,
        "fallback",
        decision,
        score,
        direction,
        List.of(
            "当前结果为 fallback 分析：LLM 未启用、未配置 key、调用失败或返回内容解析失败。",
            "岗位文本中包含后端或 AI 应用相关信息，可先加入人工复核列表。",
            "建议结合 Excel 跟进表中的投递状态、沟通状态和面试记录继续判断。"
        ),
        List.of(
            "请人工确认出勤周期、实习时长和岗位方向是否与个人计划匹配。",
            "如果 JD 描述较短，评分结果只作为初筛参考。"
        ),
        List.of(
            "Java/Spring/MySQL/Redis 或 AI 应用项目经历",
            "接口开发、问题排查、业务理解与沟通能力",
            "可结合个人项目经历补充岗位相关证据"
        ),
        List.of(
            "介绍一个与岗位技术栈相关的后端或 AI 应用项目",
            "说明接口设计、数据库设计或服务联调中的具体工作",
            "准备回答为什么选择该岗位方向"
        ),
        "您好，我对这个岗位比较感兴趣，也有相关后端开发/AI 应用项目经历，想进一步了解岗位的具体工作内容和实习安排。"
    );
  }

  private String detectMockDirection(JobAnalyzeRequest request) {
    String text = String.join(" ",
        valueOrEmpty(request.jobTitle()),
        valueOrEmpty(request.jobText())
    );

    if (text.matches("(?i).*?(AI|Agent|RAG|大模型|智能体).*")) {
      return "AI应用后端";
    }
    if (text.matches("(?i).*?(Java|Spring|MySQL|Redis|后端).*")) {
      return "Java后端";
    }
    return "待人工确认";
  }

  private String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }
}
