package com.lt.aijobscreeningagent.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RagRetrievalServiceTest {

  @Mock
  private UserProfileRagService keywordService;

  @Mock
  private VectorSearchService vectorSearchService;

  @Test
  void hybridRetrievalRanksSemanticAndKeywordEvidenceTogether() {
    List<ProfileSearchChunkResponse> keyword = List.of(
        new ProfileSearchChunkResponse(1L, "AI Agent项目", "使用RAG构建岗位匹配Agent", 8, "manual_profile", "PROJECT", null, null, null),
        new ProfileSearchChunkResponse(2L, "RAG技能", "RAG、Embedding、Java", 5, "manual_profile", "SKILL", null, null, null),
        new ProfileSearchChunkResponse(3L, "Java后端项目", "Spring Boot、MySQL、Redis", 3, "manual_profile", "PROJECT", null, null, null)
    );
    List<ProfileSearchChunkResponse> vector = List.of(
        new ProfileSearchChunkResponse(1L, "AI Agent项目", "使用RAG构建岗位匹配Agent", 95, "manual_profile", "PROJECT", .95, null, .95),
        new ProfileSearchChunkResponse(2L, "RAG技能", "RAG、Embedding、Java", 90, "manual_profile", "SKILL", .90, null, .90),
        new ProfileSearchChunkResponse(3L, "Java后端项目", "Spring Boot、MySQL、Redis", 85, "manual_profile", "PROJECT", .85, null, .85)
    );
    when(keywordService.searchProfileChunks("default", "AI应用开发实习生", 10)).thenReturn(keyword);
    when(vectorSearchService.search("default", "AI应用开发实习生", 10)).thenReturn(vector);

    RagRetrievalService service = new RagRetrievalService(keywordService, vectorSearchService);
    RagRetrievalService.RagRetrievalResult result = service.retrieveWithTrace("default", "AI应用开发实习生", 5);

    assertEquals("HYBRID", result.mode());
    assertEquals(3, result.chunks().size());
    assertEquals(1L, result.chunks().get(0).id());
    assertTrue(result.chunks().get(0).semanticScore() >= 0.0 && result.chunks().get(0).semanticScore() <= 1.0);
    assertTrue(result.chunks().get(0).keywordScore() >= 0.0 && result.chunks().get(0).keywordScore() <= 1.0);
    assertTrue(result.chunks().get(0).finalScore() >= result.chunks().get(2).finalScore());
  }
}
