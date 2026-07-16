package com.lt.aijobscreeningagent.resume;

import java.io.File;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class PdfResumeParser implements ResumeParser {

  @Override
  public ResumeRawText parse(File file) {
    if (file == null || !file.isFile()) {
      throw new IllegalArgumentException("Resume PDF file does not exist");
    }
    try (PDDocument document = Loader.loadPDF(file)) {
      String text = new PDFTextStripper().getText(document);
      return new ResumeRawText(
          text == null ? "" : text.trim(),
          file.getName(),
          document.getNumberOfPages()
      );
    } catch (IOException e) {
      throw new IllegalStateException("Failed to parse resume PDF", e);
    }
  }
}
