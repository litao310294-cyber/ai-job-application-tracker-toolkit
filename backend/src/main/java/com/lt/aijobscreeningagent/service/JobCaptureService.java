package com.lt.aijobscreeningagent.service;

import com.lt.aijobscreeningagent.dto.JobCaptureResponse;
import com.lt.aijobscreeningagent.dto.StructuredJobInfo;
import com.lt.aijobscreeningagent.repository.JobRecordRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class JobCaptureService {

  private static final int CAPTURE_FIELD_COUNT = 9;

  private final JobRecordRepository jobRecordRepository;

  public JobCaptureService(JobRecordRepository jobRecordRepository) {
    this.jobRecordRepository = jobRecordRepository;
  }

  public JobCaptureResponse capture(StructuredJobInfo jobInfo) {
    if (jobInfo == null) {
      throw new IllegalArgumentException("structured job info is required");
    }
    long jobRecordId = jobRecordRepository.saveCapturedJobRecord(jobInfo);
    return new JobCaptureResponse(
        true,
        jobRecordId,
        true,
        completenessScore(jobInfo),
        jobInfo.extractionMode()
    );
  }

  private int completenessScore(StructuredJobInfo jobInfo) {
    int present = 0;
    present += present(jobInfo.jobTitle());
    present += present(jobInfo.companyName());
    present += present(jobInfo.salary());
    present += present(jobInfo.city());
    present += present(jobInfo.education());
    present += present(jobInfo.experience());
    present += present(jobInfo.skills());
    present += present(jobInfo.jobTags());
    present += present(jobInfo.rawJD());
    return Math.round((present * 100.0f) / CAPTURE_FIELD_COUNT);
  }

  private int present(String value) {
    return value == null || value.isBlank() ? 0 : 1;
  }

  private int present(List<String> values) {
    return values == null || values.stream().allMatch(value -> value == null || value.isBlank()) ? 0 : 1;
  }
}
