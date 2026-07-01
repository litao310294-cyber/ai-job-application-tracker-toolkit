package com.lt.aijobscreeningagent.controller;

import com.lt.aijobscreeningagent.dto.JobFeedbackRequest;
import com.lt.aijobscreeningagent.dto.JobFeedbackResponse;
import com.lt.aijobscreeningagent.repository.JobFeedbackRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/job/feedback")
public class JobFeedbackController {

  private final JobFeedbackRepository jobFeedbackRepository;

  public JobFeedbackController(JobFeedbackRepository jobFeedbackRepository) {
    this.jobFeedbackRepository = jobFeedbackRepository;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public JobFeedbackResponse create(@RequestBody JobFeedbackRequest request) {
    if (request.jobRecordId() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "jobRecordId is required");
    }
    return jobFeedbackRepository.save(request);
  }

  @GetMapping
  public List<JobFeedbackResponse> list(@RequestParam Long jobRecordId) {
    if (jobRecordId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "jobRecordId is required");
    }
    return jobFeedbackRepository.findByJobRecordId(jobRecordId);
  }
}
