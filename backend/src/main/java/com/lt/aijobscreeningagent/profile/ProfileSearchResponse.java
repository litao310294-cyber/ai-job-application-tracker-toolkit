package com.lt.aijobscreeningagent.profile;

import java.util.List;

public record ProfileSearchResponse(
    boolean exists,
    String query,
    int topK,
    List<ProfileSearchChunkResponse> chunks
) {
}
