package com.lt.aijobscreeningagent.service.analysis;

import com.lt.aijobscreeningagent.profile.ProfileSearchChunkResponse;
import java.util.List;
import org.springframework.stereotype.Service;

/** Formats retrieved evidence into bounded, typed context for the LLM. */
@Service
public class RagContextBuilder {

  public RagContext build(List<ProfileSearchChunkResponse> chunks) {
    if (chunks == null || chunks.isEmpty()) {
      return new RagContext("【用户能力画像】\n未检索到相关用户画像资料。", List.of());
    }
    StringBuilder builder = new StringBuilder();
    String currentSection = null;
    for (int index = 0; index < chunks.size(); index++) {
      ProfileSearchChunkResponse chunk = chunks.get(index);
      String section = "FEEDBACK".equalsIgnoreCase(value(chunk.chunkType()))
          ? "【用户历史行为反馈】"
          : "【用户能力画像】";
      if (!section.equals(currentSection)) {
        if (builder.length() > 0) {
          builder.append("\n");
        }
        builder.append(section).append("\n");
        currentSection = section;
      }
      builder.append("资料").append(index + 1).append("：\n")
          .append("标题：").append(value(chunk.title())).append("\n")
          .append("类型：").append(value(chunk.chunkType())).append("\n")
          .append("内容：").append(value(chunk.content())).append("\n")
          .append("来源：").append(value(chunk.sourceType())).append("\n")
          .append("分数：").append(chunk.score()).append("\n");
      if (chunk.semanticScore() != null) {
        builder.append("semanticScore：").append(chunk.semanticScore()).append("\n");
      }
      if (chunk.keywordScore() != null) {
        builder.append("keywordScore：").append(chunk.keywordScore()).append("\n");
      }
      if (chunk.chunkWeight() != null) {
        builder.append("chunkWeight：").append(chunk.chunkWeight()).append("\n");
      }
      if (chunk.baseScore() != null) {
        builder.append("baseScore：").append(chunk.baseScore()).append("\n");
      }
      if (chunk.finalScore() != null) {
        builder.append("finalScore：").append(chunk.finalScore()).append("\n");
      }
      builder.append("\n");
    }
    return new RagContext(builder.toString().trim(), List.copyOf(chunks));
  }

  private String value(String value) {
    return value == null ? "" : value;
  }
}
