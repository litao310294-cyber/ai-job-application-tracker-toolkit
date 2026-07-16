package com.lt.aijobscreeningagent.resume;

public record ResumeRawText(
    String text,
    String fileName,
    int pageCount
) {
}
