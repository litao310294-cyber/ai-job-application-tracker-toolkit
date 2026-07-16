package com.lt.aijobscreeningagent.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lt.aijobscreeningagent.dto.JobAnalyzeRequest;
import com.lt.aijobscreeningagent.dto.JobAnalyzeResponse;
import com.lt.aijobscreeningagent.dto.JobRecordSummary;
import com.lt.aijobscreeningagent.dto.StructuredJobInfo;
import com.lt.aijobscreeningagent.service.JobFieldSanitizer;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JobRecordRepository {

  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 100;

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final JobFieldSanitizer jobFieldSanitizer;

  public JobRecordRepository(
      JdbcTemplate jdbcTemplate,
      ObjectMapper objectMapper,
      JobFieldSanitizer jobFieldSanitizer
  ) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
    this.jobFieldSanitizer = jobFieldSanitizer;
  }

  public long saveJobRecord(JobAnalyzeRequest request) {
    String sql = """
        insert into job_record (
          job_title,
          company_name,
          salary,
          city,
          schedule_text,
          duration_text,
          job_text,
          rule_score,
          rule_conclusion,
          created_at,
          updated_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
        """;

    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, jobFieldSanitizer.sanitizeJobTitle(request.jobTitle()));
      ps.setString(2, jobFieldSanitizer.sanitizeCompanyName(request.companyName()));
      ps.setString(3, jobFieldSanitizer.sanitizeShortField(request.salary(), 100));
      ps.setString(4, jobFieldSanitizer.sanitizeShortField(request.city(), 100));
      ps.setString(5, jobFieldSanitizer.sanitizeShortField(request.schedule(), 100));
      ps.setString(6, jobFieldSanitizer.sanitizeShortField(request.duration(), 100));
      ps.setString(7, request.jobText());
      if (request.ruleScore() == null) {
        ps.setObject(8, null);
      } else {
        ps.setInt(8, request.ruleScore());
      }
      ps.setString(9, request.ruleConclusion());
      return ps;
    }, keyHolder);

    Number key = keyHolder.getKey();
    if (key == null) {
      throw new IllegalStateException("Failed to get generated job_record id");
    }
    return key.longValue();
  }

  public long saveCapturedJobRecord(StructuredJobInfo jobInfo) {
    String sql = """
        insert into job_record (
          job_title,
          company_name,
          salary,
          city,
          education,
          experience,
          skills_json,
          tags_json,
          capture_source,
          job_text,
          created_at,
          updated_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
        """;

    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, jobFieldSanitizer.sanitizeJobTitle(jobInfo.jobTitle()));
      ps.setString(2, jobFieldSanitizer.sanitizeCompanyName(jobInfo.companyName()));
      ps.setString(3, jobFieldSanitizer.sanitizeShortField(jobInfo.salary(), 100));
      ps.setString(4, jobFieldSanitizer.sanitizeShortField(jobInfo.city(), 100));
      ps.setString(5, jobFieldSanitizer.sanitizeShortField(jobInfo.education(), 100));
      ps.setString(6, jobFieldSanitizer.sanitizeShortField(jobInfo.experience(), 100));
      ps.setString(7, toJson(jobInfo.skills()));
      ps.setString(8, toJson(jobInfo.jobTags()));
      ps.setString(9, jobFieldSanitizer.sanitizeShortField(jobInfo.extractionMode(), 20));
      ps.setString(10, jobInfo.rawJD());
      return ps;
    }, keyHolder);

    Number key = keyHolder.getKey();
    if (key == null) {
      throw new IllegalStateException("Failed to get generated captured job_record id");
    }
    return key.longValue();
  }

  public boolean existsJobRecord(long jobRecordId) {
    Integer count = jdbcTemplate.queryForObject(
        "select count(*) from job_record where id = ?",
        Integer.class,
        jobRecordId
    );
    return count != null && count > 0;
  }

  public Optional<StructuredJobInfo> findStructuredJobInfo(long jobRecordId) {
    List<StructuredJobInfo> records = jdbcTemplate.query("""
        select job_title, company_name, salary, city, education, experience,
               skills_json, tags_json, job_text, capture_source
        from job_record
        where id = ?
        limit 1
        """, (rs, rowNum) -> new StructuredJobInfo(
        rs.getString("job_title"),
        rs.getString("company_name"),
        rs.getString("salary"),
        rs.getString("city"),
        rs.getString("education"),
        rs.getString("experience"),
        fromJson(rs.getString("skills_json")),
        fromJson(rs.getString("tags_json")),
        rs.getString("job_text"),
        rs.getString("capture_source")
    ), jobRecordId);
    return records.stream().findFirst();
  }

  public void updateRuleResult(long jobRecordId, Integer ruleScore, String ruleConclusion) {
    jdbcTemplate.update(
        "update job_record set rule_score = ?, rule_conclusion = ?, updated_at = now() where id = ?",
        ruleScore,
        ruleConclusion,
        jobRecordId
    );
  }

  public void saveJobAnalysis(long jobRecordId, JobAnalyzeResponse response) {
    String sql = """
        insert into job_analysis (
          job_record_id,
          task_id,
          status,
          decision,
          score,
          direction,
          reasons_json,
          risks_json,
          resume_matches_json,
          interview_focus_json,
          suggested_message,
          created_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
        """;

    jdbcTemplate.update(
        sql,
        jobRecordId,
        response.taskId(),
        response.status(),
        response.decision(),
        response.score(),
        response.direction(),
        toJson(response.reasons()),
        toJson(response.risks()),
        toJson(response.resumeMatches()),
        toJson(response.interviewFocus()),
        response.suggestedMessage()
    );
  }

  public List<JobRecordSummary> findRecentRecords(Integer limit) {
    int safeLimit = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
    String sql = """
        select
          jr.id as job_record_id,
          jr.job_title,
          jr.company_name,
          jr.salary,
          jr.city,
          jr.rule_score,
          jr.rule_conclusion,
          ja.decision as ai_decision,
          ja.score as ai_score,
          ja.direction as ai_direction,
          ja.status,
          ja.created_at
        from job_record jr
        join job_analysis ja on ja.job_record_id = jr.id
        order by ja.created_at desc, ja.id desc
        limit ?
        """;

    return jdbcTemplate.query(sql, summaryMapper(), safeLimit);
  }

  private RowMapper<JobRecordSummary> summaryMapper() {
    return (rs, rowNum) -> {
      Timestamp createdAt = rs.getTimestamp("created_at");
      LocalDateTime createdAtValue = createdAt == null ? null : createdAt.toLocalDateTime();
      return new JobRecordSummary(
          rs.getLong("job_record_id"),
          rs.getString("job_title"),
          rs.getString("company_name"),
          rs.getString("salary"),
          rs.getString("city"),
          (Integer) rs.getObject("rule_score"),
          rs.getString("rule_conclusion"),
          rs.getString("ai_decision"),
          (Integer) rs.getObject("ai_score"),
          rs.getString("ai_direction"),
          rs.getString("status"),
          createdAtValue
      );
    };
  }

  private String toJson(List<String> values) {
    try {
      return objectMapper.writeValueAsString(values == null ? List.of() : values);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize analysis list field", e);
    }
  }

  private List<String> fromJson(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(json, objectMapper.getTypeFactory()
          .constructCollectionType(List.class, String.class));
    } catch (JsonProcessingException e) {
      return List.of();
    }
  }
}
