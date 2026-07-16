package com.lt.aijobscreeningagent.controller;

import com.lt.aijobscreeningagent.dto.JobAnalyzeRequest;
import com.lt.aijobscreeningagent.dto.JobAnalyzeResponse;
import com.lt.aijobscreeningagent.dto.JobRecordSummary;
import com.lt.aijobscreeningagent.dto.JobCaptureResponse;
import com.lt.aijobscreeningagent.dto.StructuredJobInfo;
import com.lt.aijobscreeningagent.service.JobCaptureService;
import com.lt.aijobscreeningagent.service.JobAnalysisService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/job")
public class JobAnalysisController {

  private final JobAnalysisService jobAnalysisService;
  private final JobCaptureService jobCaptureService;

  public JobAnalysisController(JobAnalysisService jobAnalysisService, JobCaptureService jobCaptureService) {
    this.jobAnalysisService = jobAnalysisService;
    this.jobCaptureService = jobCaptureService;
  }

  @PostMapping("/capture")
  public JobCaptureResponse capture(@RequestBody StructuredJobInfo jobInfo) {
    return jobCaptureService.capture(jobInfo);
  }

  @PostMapping("/analyze")
  public JobAnalyzeResponse analyze(@RequestBody JobAnalyzeRequest request) {
    return jobAnalysisService.analyze(request);
  }

  @GetMapping("/records")
  public List<JobRecordSummary> records(@RequestParam(required = false, defaultValue = "20") Integer limit) {
    int safeLimit = limit == null ? 20 : Math.max(1, Math.min(limit, 100));
    return jobAnalysisService.findRecentRecords(safeLimit);
  }
}
