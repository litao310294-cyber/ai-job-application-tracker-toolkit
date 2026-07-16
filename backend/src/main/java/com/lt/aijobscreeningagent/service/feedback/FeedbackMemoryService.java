package com.lt.aijobscreeningagent.service.feedback;

import com.lt.aijobscreeningagent.dto.JobFeedbackResponse;
import com.lt.aijobscreeningagent.dto.StructuredJobInfo;
import com.lt.aijobscreeningagent.profile.ProfileEmbeddingIndexService;
import com.lt.aijobscreeningagent.profile.UserProfileRagRepository;
import com.lt.aijobscreeningagent.repository.JobRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Best-effort bridge from job feedback persistence to profile RAG memory. */
@Service
public class FeedbackMemoryService {

  private static final Logger log = LoggerFactory.getLogger(FeedbackMemoryService.class);
  private static final String DEFAULT_PROFILE_NAME = "default";

  private final JobRecordRepository jobRecordRepository;
  private final UserProfileRagRepository userProfileRagRepository;
  private final ProfileEmbeddingIndexService profileEmbeddingIndexService;
  private final FeedbackChunkBuilder feedbackChunkBuilder;

  public FeedbackMemoryService(
      JobRecordRepository jobRecordRepository,
      UserProfileRagRepository userProfileRagRepository,
      ProfileEmbeddingIndexService profileEmbeddingIndexService,
      FeedbackChunkBuilder feedbackChunkBuilder
  ) {
    this.jobRecordRepository = jobRecordRepository;
    this.userProfileRagRepository = userProfileRagRepository;
    this.profileEmbeddingIndexService = profileEmbeddingIndexService;
    this.feedbackChunkBuilder = feedbackChunkBuilder;
  }

  /** Invoked after the original job_feedback insert has succeeded. */
  public void onFeedbackSaved(Long jobRecordId, JobFeedbackResponse feedback) {
    createFeedbackChunk(jobRecordId, feedback);
  }

  /** Creates one FEEDBACK chunk and refreshes its embedding when possible. */
  public void createFeedbackChunk(Long jobRecordId, JobFeedbackResponse feedback) {
    try {
      Long documentId = userProfileRagRepository.findLatestDocumentId(DEFAULT_PROFILE_NAME).orElse(null);
      if (documentId == null) {
        log.info("Skip feedback memory: default profile document does not exist. jobRecordId={}", jobRecordId);
        return;
      }
      StructuredJobInfo job = jobRecordId == null
          ? null
          : jobRecordRepository.findStructuredJobInfo(jobRecordId).orElse(null);
      FeedbackChunkBuilder.FeedbackChunk chunk = feedbackChunkBuilder.build(job, feedback);
      int nextIndex = userProfileRagRepository.findNextChunkIndex(DEFAULT_PROFILE_NAME, documentId);
      String contentHash = sha256(chunk.title() + "\n" + chunk.content());
      userProfileRagRepository.saveChunk(
          DEFAULT_PROFILE_NAME,
          documentId,
          nextIndex,
          chunk.title(),
          chunk.content(),
          contentHash,
          0,
          chunk.sourceType(),
          chunk.chunkType(),
          chunk.chunkWeight(),
          chunk.metadataJson());
      if (feedback != null && feedback.id() != null) {
        userProfileRagRepository.bumpLatestDocumentVersion(DEFAULT_PROFILE_NAME, "feedback:" + feedback.id());
      }
      profileEmbeddingIndexService.reindex(DEFAULT_PROFILE_NAME);
      log.info("Feedback memory chunk created. jobRecordId={}, feedbackId={}, chunkIndex={}",
          jobRecordId, feedback == null ? null : feedback.id(), nextIndex);
    } catch (RuntimeException ex) {
      // Feedback persistence is the source of truth; memory enrichment must not break it.
      log.warn("Failed to create feedback memory chunk. jobRecordId={}", jobRecordId, ex);
    }
  }

  public String buildFeedbackContent(StructuredJobInfo job, JobFeedbackResponse feedback) {
    return feedbackChunkBuilder.build(job, feedback).content();
  }

  private String sha256(String value) {
    try {
      var digest = java.security.MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder();
      for (byte item : hash) {
        result.append(String.format("%02x", item));
      }
      return result.toString();
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
