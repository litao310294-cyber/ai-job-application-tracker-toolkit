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

  @Test
  void projectWeightCanMoveProjectAboveHigherKeywordSkillHit() {
    List<ProfileSearchChunkResponse> keyword = List.of(
        new ProfileSearchChunkResponse(10L, "技能栈", "Java RAG Redis", 10, "resume", "SKILL",
            null, null, null, 1.2d, null),
        new ProfileSearchChunkResponse(11L, "AI Agent 项目", "构建岗位匹配 Agent", 9, "resume", "PROJECT",
            null, null, null, 1.3d, null),
        new ProfileSearchChunkResponse(12L, "教育背景", "本科", 0, "resume", "EDUCATION",
            null, null, null, 0.8d, null)
    );
    List<ProfileSearchChunkResponse> vector = List.of(
        new ProfileSearchChunkResponse(10L, "技能栈", "Java RAG Redis", 95, "resume", "SKILL",
            .95, null, .95, 1.2d, null),
        new ProfileSearchChunkResponse(11L, "AI Agent 项目", "构建岗位匹配 Agent", 86, "resume", "PROJECT",
            .90, null, .90, 1.3d, null)
    );
    when(keywordService.searchProfileChunks("default", "AI应用开发", 10)).thenReturn(keyword);
    when(vectorSearchService.search("default", "AI应用开发", 10)).thenReturn(vector);

    RagRetrievalService.RagRetrievalResult result = new RagRetrievalService(keywordService, vectorSearchService)
        .retrieveWithTrace("default", "AI应用开发", 5);

    assertEquals("PROJECT", result.chunks().get(0).chunkType());
    assertEquals(1.3d, result.chunks().get(0).chunkWeight());
    assertTrue(result.chunks().get(0).finalScore() > result.chunks().get(1).finalScore());
  }

  @Test
  void projectEvidenceRanksAboveFeedbackWithSameRetrievalScores() {
    String query = "AI应用开发";
    List<ProfileSearchChunkResponse> keyword = List.of(
        new ProfileSearchChunkResponse(20L, "历史行为反馈", "曾投递 AI 应用开发岗位", 8,
            "job_feedback", "FEEDBACK", null, null, null, 0.7d, null),
        new ProfileSearchChunkResponse(21L, "AI Agent 项目", "构建 AI 求职 Agent", 8,
            "resume", "PROJECT", null, null, null, 1.3d, null));
    List<ProfileSearchChunkResponse> vector = List.of(
        new ProfileSearchChunkResponse(20L, "历史行为反馈", "曾投递 AI 应用开发岗位", 85,
            "job_feedback", "FEEDBACK", .85, null, .85, 0.7d, null),
        new ProfileSearchChunkResponse(21L, "AI Agent 项目", "构建 AI 求职 Agent", 85,
            "resume", "PROJECT", .85, null, .85, 1.3d, null));
    when(keywordService.searchProfileChunks("default", query, 10)).thenReturn(keyword);
    when(vectorSearchService.search("default", query, 10)).thenReturn(vector);

    List<ProfileSearchChunkResponse> result = new RagRetrievalService(keywordService, vectorSearchService)
        .retrieveWithTrace("default", query, 5).chunks();

    assertEquals("PROJECT", result.get(0).chunkType());
    assertEquals("FEEDBACK", result.get(1).chunkType());
    assertTrue(result.get(0).finalScore() > result.get(1).finalScore());
    assertEquals(0.7d, result.get(1).chunkWeight());
  }

  @Test
  void feedbackDefaultWeightIsAppliedWhenChunkMetadataHasNoWeight() {
    String query = "AI Agent";
    List<ProfileSearchChunkResponse> keyword = List.of(
        new ProfileSearchChunkResponse(30L, "反馈", "进入面试", 5, "job_feedback", "FEEDBACK",
            null, null, null));
    List<ProfileSearchChunkResponse> vector = List.of(
        new ProfileSearchChunkResponse(30L, "反馈", "进入面试", 80, "job_feedback", "FEEDBACK",
            .8, null, .8));
    when(keywordService.searchProfileChunks("default", query, 10)).thenReturn(keyword);
    when(vectorSearchService.search("default", query, 10)).thenReturn(vector);

    ProfileSearchChunkResponse result = new RagRetrievalService(keywordService, vectorSearchService)
        .retrieveWithTrace("default", query, 1).chunks().get(0);

    assertEquals(0.7d, result.chunkWeight());
    assertTrue(result.finalScore() < result.semanticScore());
  }
}
