package com.lt.aijobscreeningagent.controller;

import com.lt.aijobscreeningagent.service.trace.JobAnalysisTrace;
import com.lt.aijobscreeningagent.service.trace.JobAnalysisTraceRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trace")
public class JobAnalysisTraceController {

  private final JobAnalysisTraceRepository repository;

  public JobAnalysisTraceController(JobAnalysisTraceRepository repository) {
    this.repository = repository;
  }

  @GetMapping("/{taskId}")
  public List<JobAnalysisTrace> findByTaskId(@PathVariable String taskId) {
    return repository.findByTaskId(taskId);
  }
}
