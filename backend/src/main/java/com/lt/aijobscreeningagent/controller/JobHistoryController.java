package com.lt.aijobscreeningagent.controller;

import com.lt.aijobscreeningagent.dto.JobHistoryRecord;
import com.lt.aijobscreeningagent.repository.JobHistoryRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/jobs")
public class JobHistoryController {

  private final JobHistoryRepository jobHistoryRepository;

  public JobHistoryController(JobHistoryRepository jobHistoryRepository) {
    this.jobHistoryRepository = jobHistoryRepository;
  }

  @GetMapping("/recent")
  public List<JobHistoryRecord> recent(@RequestParam(required = false, defaultValue = "20") Integer limit) {
    return jobHistoryRepository.findRecent(limit);
  }

  @GetMapping("/{jobRecordId}")
  public JobHistoryRecord detail(@PathVariable Long jobRecordId) {
    return jobHistoryRepository.findById(jobRecordId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job record not found"));
  }

  @GetMapping("/search")
  public List<JobHistoryRecord> search(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false, defaultValue = "20") Integer limit
  ) {
    return jobHistoryRepository.search(keyword, limit);
  }

  @GetMapping("/match")
  public List<JobHistoryRecord> match(
      @RequestParam(required = false) String companyName,
      @RequestParam(required = false) String jobTitle
  ) {
    return jobHistoryRepository.match(companyName, jobTitle);
  }
}
