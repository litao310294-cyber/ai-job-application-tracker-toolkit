package com.lt.aijobscreeningagent.service.trace;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobAnalysisTraceServiceTest {

  @Mock
  private JobAnalysisTraceRepository repository;

  @Test
  void finishPersistsStructuredOutputAndLatency() {
    when(repository.insertStart("task-1", 9L, "RAG_RETRIEVAL", "{\"query\":\"Java\"}"))
        .thenReturn(7L);
    JobAnalysisTraceService service = new JobAnalysisTraceService(repository, new ObjectMapper());

    JobAnalysisTraceService.TraceHandle handle = service.start(
        "task-1", 9L, "RAG_RETRIEVAL", Map.of("query", "Java"));
    service.finish(handle, Map.of("retrievalMode", "HYBRID", "chunkCount", 3));

    verify(repository).finish(eq(7L), contains("HYBRID"), anyLong());
  }

  @Test
  void failurePersistsErrorInformation() {
    when(repository.insertStart("task-2", 9L, "LLM_CALL", "{}"))
        .thenReturn(8L);
    JobAnalysisTraceService service = new JobAnalysisTraceService(repository, new ObjectMapper());

    JobAnalysisTraceService.TraceHandle handle = service.start("task-2", 9L, "LLM_CALL", Map.of());
    service.fail(handle, new IllegalStateException("DeepSeek timeout"));

    verify(repository).finish(eq(8L), contains("DeepSeek timeout"), anyLong());
  }
}
