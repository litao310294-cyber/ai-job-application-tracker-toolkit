package com.lt.aijobscreeningagent.service.analysis;

import com.lt.aijobscreeningagent.profile.ProfileSearchChunkResponse;
import java.util.List;

public record RagContext(
    String text,
    List<ProfileSearchChunkResponse> chunks
) {
}
