package com.lt.aijobscreeningagent.profile;

public record ProfileReindexResponse(
    boolean success,
    String profileName,
    Long documentId,
    int chunkCount,
    int deletedDocumentCount,
    int deletedChunkCount
) {
}
