package com.lt.aijobscreeningagent.resume;

import com.lt.aijobscreeningagent.profile.ProfileReindexResponse;
import com.lt.aijobscreeningagent.profile.UserProfileRepository;
import com.lt.aijobscreeningagent.profile.UserProfileRagService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ResumeUploadService {

  private static final Logger log = LoggerFactory.getLogger(ResumeUploadService.class);
  private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

  private final ResumeParser resumeParser;
  private final ResumeProfileLlmService profileLlmService;
  private final UserProfileRepository userProfileRepository;
  private final UserProfileRagService userProfileRagService;

  public ResumeUploadService(
      ResumeParser resumeParser,
      ResumeProfileLlmService profileLlmService,
      UserProfileRepository userProfileRepository,
      UserProfileRagService userProfileRagService
  ) {
    this.resumeParser = resumeParser;
    this.profileLlmService = profileLlmService;
    this.userProfileRepository = userProfileRepository;
    this.userProfileRagService = userProfileRagService;
  }

  public ResumeUploadResponse upload(MultipartFile multipartFile) {
    validate(multipartFile);
    String originalName = multipartFile.getOriginalFilename() == null
        ? "resume.pdf"
        : Path.of(multipartFile.getOriginalFilename()).getFileName().toString();
    Path tempPath = null;
    try {
      tempPath = Files.createTempFile("resume-", ".pdf");
      multipartFile.transferTo(tempPath);
      ResumeRawText rawText = resumeParser.parse(tempPath.toFile());
      log.info("Resume PDF parsed. fileName={}, pages={}, textLength={}",
          originalName, rawText.pageCount(), rawText.text().length());
      ResumeProfileExtraction extraction = profileLlmService.extract(rawText);
      userProfileRepository.saveResumeProfile(extraction, rawText.text());
      ProfileReindexResponse reindex = userProfileRagService.reindexDefaultProfile(false);
      log.info("Resume profile indexed. fileName={}, chunkCount={}", originalName, reindex.chunkCount());
      return new ResumeUploadResponse(
          true,
          originalName,
          rawText.pageCount(),
          rawText.text().length(),
          reindex.documentId(),
          reindex.chunkCount(),
          "Resume imported and profile index rebuilt"
      );
    } catch (IOException e) {
      throw new IllegalStateException("Failed to store uploaded resume", e);
    } finally {
      if (tempPath != null) {
        try {
          Files.deleteIfExists(tempPath);
        } catch (IOException e) {
          log.warn("Failed to delete temporary resume file: {}", tempPath, e);
        }
      }
    }
  }

  private void validate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("Resume PDF is required");
    }
    if (file.getSize() > MAX_FILE_SIZE) {
      throw new IllegalArgumentException("Resume PDF must be smaller than 10 MB");
    }
    String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
    String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
    if (!name.endsWith(".pdf") && !contentType.equals("application/pdf")) {
      throw new IllegalArgumentException("Only PDF resumes are supported");
    }
  }
}
