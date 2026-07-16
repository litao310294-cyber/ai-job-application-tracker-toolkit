package com.lt.aijobscreeningagent.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lt.aijobscreeningagent.dto.JobDebugCaptureRequest;
import com.lt.aijobscreeningagent.dto.JobDebugCaptureResponse;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/job")
public class JobDebugCaptureController {

  private static final Logger log = LoggerFactory.getLogger(JobDebugCaptureController.class);
  private final ObjectMapper objectMapper;

  public JobDebugCaptureController(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @PostMapping("/debug-capture")
  public JobDebugCaptureResponse capture(@RequestBody JobDebugCaptureRequest request) {
    String receivedAt = OffsetDateTime.now().toString();
    JsonNode structuredJobInfo = request.structuredJobInfo();
    String jobTitle = textValue(structuredJobInfo, "jobTitle");
    String companyName = textValue(structuredJobInfo, "companyName");

    log.info("debug structuredJobInfo JSON:\n{}", formatJson(structuredJobInfo));
    log.info(
        "debug structuredJobInfo fields: jobTitle={}, companyName={}, salary={}, city={}, "
            + "education={}, experience={}, skills={}, jobTags={}, rawJDLength={}",
        jobTitle,
        companyName,
        textValue(structuredJobInfo, "salary"),
        textValue(structuredJobInfo, "city"),
        textValue(structuredJobInfo, "education"),
        textValue(structuredJobInfo, "experience"),
        jsonValue(structuredJobInfo, "skills"),
        jsonValue(structuredJobInfo, "jobTags"),
        textValue(structuredJobInfo, "rawJD").length());
    log.info("debug job capture received: title={}, company={}, sourceUrl={}, receivedAt={}",
        jobTitle, companyName, request.sourceUrl(), receivedAt);
    return new JobDebugCaptureResponse(
        true,
        receivedAt,
        "debug job capture received",
        request.structuredJobInfo(),
        request.bossHelperJobData()
    );
  }

  private String textValue(JsonNode node, String fieldName) {
    return node == null ? "" : node.path(fieldName).asText("");
  }

  private String jsonValue(JsonNode node, String fieldName) {
    if (node == null || node.path(fieldName).isMissingNode() || node.path(fieldName).isNull()) {
      return "[]";
    }
    return node.path(fieldName).toString();
  }

  private String formatJson(JsonNode node) {
    if (node == null || node.isNull()) {
      return "null";
    }
    try {
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    } catch (JsonProcessingException exception) {
      log.warn("failed to format structuredJobInfo JSON", exception);
      return node.toString();
    }
  }
}
