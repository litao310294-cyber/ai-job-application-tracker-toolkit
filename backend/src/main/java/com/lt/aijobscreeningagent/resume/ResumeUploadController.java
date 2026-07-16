package com.lt.aijobscreeningagent.resume;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/profile/resume")
public class ResumeUploadController {

  private final ResumeUploadService resumeUploadService;

  public ResumeUploadController(ResumeUploadService resumeUploadService) {
    this.resumeUploadService = resumeUploadService;
  }

  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResumeUploadResponse upload(@RequestPart("file") MultipartFile file) {
    return resumeUploadService.upload(file);
  }
}
