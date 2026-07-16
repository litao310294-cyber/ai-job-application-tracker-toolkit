package com.lt.aijobscreeningagent.service.analysis;

import com.lt.aijobscreeningagent.dto.JobAnalyzeProfileRag;
import com.lt.aijobscreeningagent.dto.JobAnalyzeRequest;
import com.lt.aijobscreeningagent.dto.JobAnalyzeResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FallbackAnalysisService {

  public JobAnalyzeResponse create(
      long jobRecordId,
      String taskId,
      JobAnalyzeRequest request,
      RuleAnalysisResult rules,
      JobAnalyzeProfileRag profileRag
  ) {
    return new JobAnalyzeResponse(
        jobRecordId,
        taskId,
        "fallback",
        rules.conclusion(),
        rules.score(),
        detectDirection(request),
        List.of("当前使用fallback分析，未能完成LLM结构化分析。"),
        List.of("请人工确认出勤周期、实习时长和岗位方向。"),
        List.of("可结合用户画像中的真实技能和项目经历补充岗位证据。"),
        List.of("准备一个与岗位技术栈相关的项目说明。"),
        "您好，我对这个岗位比较感兴趣，也有相关后端或AI应用项目经历，想进一步了解岗位安排。",
        profileRag
    );
  }

  private String detectDirection(JobAnalyzeRequest request) {
    String text = (request.jobTitle() == null ? "" : request.jobTitle()) + " "
        + (request.jobText() == null ? "" : request.jobText());
    if (text.matches("(?i).*?(AI|Agent|RAG|大模型|智能体).*")) {
      return "AI应用后端";
    }
    if (text.matches("(?i).*?(Java|Spring|MySQL|Redis|后端).*")) {
      return "Java后端";
    }
    return "待人工确认";
  }
}
