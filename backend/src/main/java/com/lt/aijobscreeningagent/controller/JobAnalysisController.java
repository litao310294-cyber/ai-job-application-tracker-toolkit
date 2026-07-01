package com.lt.aijobscreeningagent.controller;

import com.lt.aijobscreeningagent.dto.JobAnalyzeRequest;
import com.lt.aijobscreeningagent.dto.JobAnalyzeResponse;
import com.lt.aijobscreeningagent.service.JobAnalysisService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/job")
public class JobAnalysisController {

  private final JobAnalysisService jobAnalysisService;

  public JobAnalysisController(JobAnalysisService jobAnalysisService) {
    this.jobAnalysisService = jobAnalysisService;
  }

  @PostMapping("/analyze")
  public JobAnalyzeResponse analyze(@RequestBody JobAnalyzeRequest request) {
    return jobAnalysisService.analyze(request);
  }
}
