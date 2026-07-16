package com.lt.aijobscreeningagent.resume;

import java.io.File;

/** Reserved parser boundary for future PDF/DOC resume ingestion. */
public interface ResumeParser {

  ResumeRawText parse(File file);
}
