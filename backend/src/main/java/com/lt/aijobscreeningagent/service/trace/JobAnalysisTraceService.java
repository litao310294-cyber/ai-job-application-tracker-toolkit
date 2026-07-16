package com.lt.aijobscreeningagent.service.trace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Best-effort task trace; trace failures never interrupt job analysis. */
@Service
public class JobAnalysisTraceService {

  private static final Logger log = LoggerFactory.getLogger(JobAnalysisTraceService.class);

  private final JobAnalysisTraceRepository repository;
  private final ObjectMapper objectMapper;

  public JobAnalysisTraceService(JobAnalysisTraceRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  public TraceHandle start(String taskId, Long jobRecordId, String stage, Object inputData) {
    long startedAt = System.nanoTime();
    try {
      long traceId = repository.insertStart(taskId, jobRecordId, stage, toJson(inputData));
      return new TraceHandle(traceId, startedAt, taskId, stage);
    } catch (RuntimeException ex) {
      log.warn("Could not start job analysis trace. taskId={}, stage={}", taskId, stage, ex);
      return new TraceHandle(null, startedAt, taskId, stage);
    }
  }

  public void finish(TraceHandle handle, Object outputData) {
    if (handle == null || handle.traceId() == null) {
      return;
    }
    long latencyMs = elapsedMillis(handle.startedAtNanos());
    try {
      repository.finish(handle.traceId(), toJson(outputData), latencyMs);
      log.debug("job analysis trace finished. taskId={}, stage={}, latencyMs={}",
          handle.taskId(), handle.stage(), latencyMs);
    } catch (RuntimeException ex) {
      log.warn("Could not finish job analysis trace. taskId={}, stage={}",
          handle.taskId(), handle.stage(), ex);
    }
  }

  public void fail(TraceHandle handle, Throwable error) {
    Map<String, Object> failure = new LinkedHashMap<>();
    failure.put("success", false);
    failure.put("errorType", error == null ? "unknown" : error.getClass().getName());
    failure.put("errorMessage", error == null ? "unknown" : String.valueOf(error.getMessage()));
    finish(handle, failure);
  }

  public int jsonLength(Object value) {
    return toJson(value).length();
  }

  private long elapsedMillis(long startedAtNanos) {
    return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
  }

  private String toJson(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof String string) {
      return string;
    }
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      return String.valueOf(value);
    }
  }

  public record TraceHandle(Long traceId, long startedAtNanos, String taskId, String stage) {
  }
}
