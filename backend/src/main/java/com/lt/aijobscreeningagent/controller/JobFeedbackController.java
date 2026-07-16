package com.lt.aijobscreeningagent.controller;

import com.lt.aijobscreeningagent.dto.JobFeedbackRequest;
import com.lt.aijobscreeningagent.dto.JobFeedbackResponse;
import com.lt.aijobscreeningagent.repository.JobFeedbackRepository;
import com.lt.aijobscreeningagent.service.feedback.FeedbackMemoryService;
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
  private final FeedbackMemoryService feedbackMemoryService;

  public JobFeedbackController(
      JobFeedbackRepository jobFeedbackRepository,
      FeedbackMemoryService feedbackMemoryService
  ) {
    this.jobFeedbackRepository = jobFeedbackRepository;
    this.feedbackMemoryService = feedbackMemoryService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public JobFeedbackResponse create(@RequestBody JobFeedbackRequest request) {
    Long jobRecordId = resolveJobRecordId(request);
    JobFeedbackResponse response = jobFeedbackRepository.save(jobRecordId, request);
    feedbackMemoryService.onFeedbackSaved(jobRecordId, response);
    return response;
  }

  @GetMapping
  public List<JobFeedbackResponse> list(@RequestParam Long jobRecordId) {
    if (jobRecordId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "jobRecordId is required");
    }
    return jobFeedbackRepository.findByJobRecordId(jobRecordId);
  }

  private Long resolveJobRecordId(JobFeedbackRequest request) {
    if (request.jobRecordId() != null) {
      return request.jobRecordId();
    }
    if (request.taskId() != null && !request.taskId().isBlank()) {
      return jobFeedbackRepository.findJobRecordIdByTaskId(request.taskId())
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No jobRecordId found by taskId"));
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "jobRecordId or taskId is required");
  }
}
