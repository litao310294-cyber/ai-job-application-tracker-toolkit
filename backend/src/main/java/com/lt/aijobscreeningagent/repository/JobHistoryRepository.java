package com.lt.aijobscreeningagent.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lt.aijobscreeningagent.dto.JobHistoryRecord;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JobHistoryRepository {

  private static final Logger log = LoggerFactory.getLogger(JobHistoryRepository.class);
  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 100;
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
  };

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public JobHistoryRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public List<JobHistoryRecord> findRecent(Integer limit) {
    return jdbcTemplate.query(baseSql() + """
        order by coalesce(ja.created_at, jr.created_at) desc, jr.id desc
        limit ?
        """, mapper(), safeLimit(limit));
  }

  public Optional<JobHistoryRecord> findById(Long jobRecordId) {
    List<JobHistoryRecord> records = jdbcTemplate.query(baseSql() + "where jr.id = ?",
        mapper(),
        jobRecordId
    );
    return records.stream().findFirst();
  }

  public List<JobHistoryRecord> search(String keyword, Integer limit) {
    String normalizedKeyword = normalize(keyword);
    if (normalizedKeyword.isBlank()) {
      return findRecent(limit);
    }

    String pattern = "%" + normalizedKeyword + "%";
    return jdbcTemplate.query(baseSql() + """
        where jr.company_name like ?
           or jr.job_title like ?
           or jr.city like ?
           or jr.job_text like ?
           or ja.direction like ?
           or ja.decision like ?
           or jf.feedback_note like ?
        order by coalesce(ja.created_at, jr.created_at) desc, jr.id desc
        limit ?
        """,
        mapper(),
        pattern,
        pattern,
        pattern,
        pattern,
        pattern,
        pattern,
        pattern,
        safeLimit(limit)
    );
  }

  public List<JobHistoryRecord> match(String companyName, String jobTitle) {
    String company = normalize(companyName);
    String title = normalize(jobTitle);
    if (company.isBlank() && title.isBlank()) {
      return List.of();
    }

    if (!company.isBlank() && !title.isBlank()) {
      return jdbcTemplate.query(baseSql() + """
          where jr.company_name like ?
            and jr.job_title like ?
          order by coalesce(ja.created_at, jr.created_at) desc, jr.id desc
          limit 5
          """, mapper(), "%" + company + "%", "%" + title + "%");
    }

    String pattern = "%" + (!company.isBlank() ? company : title) + "%";
    return jdbcTemplate.query(baseSql() + """
        where jr.company_name like ?
           or jr.job_title like ?
        order by coalesce(ja.created_at, jr.created_at) desc, jr.id desc
        limit 5
        """, mapper(), pattern, pattern);
  }

  public List<JobHistoryRecord> findRecentForRag(int limit) {
    return findRecent(Math.max(1, Math.min(limit, 50)));
  }

  private String baseSql() {
    return """
        select
          jr.id as job_record_id,
          jr.company_name,
          jr.job_title,
          jr.city,
          jr.salary,
          jr.schedule_text,
          jr.duration_text,
          jr.rule_score,
          jr.rule_conclusion,
          jr.created_at as job_created_at,
          jr.updated_at as job_updated_at,
          ja.decision as ai_decision,
          ja.score as ai_score,
          ja.direction as ai_direction,
          ja.reasons_json,
          ja.risks_json,
          ja.resume_matches_json,
          ja.interview_focus_json,
          ja.suggested_message,
          jf.apply_status,
          jf.chat_status,
          jf.interview_status,
          jf.feedback_note
        from job_record jr
        left join job_analysis ja on ja.id = (
          select ja2.id
          from job_analysis ja2
          where ja2.job_record_id = jr.id
          order by ja2.created_at desc, ja2.id desc
          limit 1
        )
        left join job_feedback jf on jf.id = (
          select jf2.id
          from job_feedback jf2
          where jf2.job_record_id = jr.id
          order by jf2.created_at desc, jf2.id desc
          limit 1
        )
        """;
  }

  private RowMapper<JobHistoryRecord> mapper() {
    return (rs, rowNum) -> new JobHistoryRecord(
        rs.getLong("job_record_id"),
        rs.getString("company_name"),
        rs.getString("job_title"),
        rs.getString("city"),
        rs.getString("salary"),
        rs.getString("schedule_text"),
        rs.getString("duration_text"),
        (Integer) rs.getObject("rule_score"),
        rs.getString("rule_conclusion"),
        rs.getString("ai_decision"),
        (Integer) rs.getObject("ai_score"),
        rs.getString("ai_direction"),
        parseStringList(rs.getString("reasons_json")),
        parseStringList(rs.getString("risks_json")),
        parseStringList(rs.getString("resume_matches_json")),
        parseStringList(rs.getString("interview_focus_json")),
        rs.getString("suggested_message"),
        rs.getString("apply_status"),
        rs.getString("chat_status"),
        rs.getString("interview_status"),
        rs.getString("feedback_note"),
        toLocalDateTime(rs.getTimestamp("job_created_at")),
        toLocalDateTime(rs.getTimestamp("job_updated_at"))
    );
  }

  private List<String> parseStringList(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      List<String> values = objectMapper.readValue(json, STRING_LIST);
      return values == null ? List.of() : values;
    } catch (JsonProcessingException e) {
      log.warn("Failed to parse job analysis JSON list field, return empty list.");
      return List.of();
    }
  }

  private int safeLimit(Integer limit) {
    if (limit == null) {
      return DEFAULT_LIMIT;
    }
    return Math.max(1, Math.min(limit, MAX_LIMIT));
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  private LocalDateTime toLocalDateTime(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toLocalDateTime();
  }
}
