package com.lt.aijobscreeningagent.service.analysis;

import com.lt.aijobscreeningagent.dto.JobAnalyzeResponse;
import com.lt.aijobscreeningagent.repository.JobRecordRepository;
import com.lt.aijobscreeningagent.service.JobAnalysisCacheService;
import org.springframework.stereotype.Service;

@Service
public class AnalysisSaver {

  private final JobRecordRepository jobRecordRepository;
  private final JobAnalysisCacheService cacheService;

  public AnalysisSaver(JobRecordRepository jobRecordRepository, JobAnalysisCacheService cacheService) {
    this.jobRecordRepository = jobRecordRepository;
    this.cacheService = cacheService;
  }

  public void save(long jobRecordId, JobAnalyzeResponse response, String cacheKey) {
    jobRecordRepository.saveJobAnalysis(jobRecordId, response);
    cacheService.put(cacheKey, response);
  }
}
