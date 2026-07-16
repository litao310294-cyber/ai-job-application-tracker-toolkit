package com.lt.aijobscreeningagent.resume;

public record ResumeUploadResponse(
    boolean success,
    String fileName,
    int pageCount,
    int textLength,
    long documentId,
    int chunkCount,
    String message
) {
}
