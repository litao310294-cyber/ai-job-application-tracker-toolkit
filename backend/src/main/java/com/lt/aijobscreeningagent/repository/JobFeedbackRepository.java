package com.lt.aijobscreeningagent.repository;

import com.lt.aijobscreeningagent.dto.JobFeedbackRequest;
import com.lt.aijobscreeningagent.dto.JobFeedbackResponse;
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
public class JobFeedbackRepository {

  private final JdbcTemplate jdbcTemplate;

  public JobFeedbackRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public JobFeedbackResponse save(Long jobRecordId, JobFeedbackRequest request) {
    String sql = """
        insert into job_feedback (
          job_record_id,
          apply_status,
          chat_status,
          interview_status,
          feedback_note,
          reject_reason,
          created_at,
          updated_at
        ) values (?, ?, ?, ?, ?, ?, now(), now())
        """;

    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
      ps.setLong(1, jobRecordId);
      ps.setString(2, request.applyStatus());
      ps.setString(3, request.chatStatus());
      ps.setString(4, request.interviewStatus());
      ps.setString(5, request.feedbackNote());
      ps.setString(6, request.rejectReason());
      return ps;
    }, keyHolder);

    Number key = keyHolder.getKey();
    if (key == null) {
      throw new IllegalStateException("Failed to get generated job_feedback id");
    }

    return findById(key.longValue());
  }

  public List<JobFeedbackResponse> findByJobRecordId(Long jobRecordId) {
    String sql = """
        select
          id,
          job_record_id,
          apply_status,
          chat_status,
          interview_status,
          feedback_note,
          reject_reason,
          created_at
        from job_feedback
        where job_record_id = ?
        order by created_at desc, id desc
        """;
    return jdbcTemplate.query(sql, mapper(), jobRecordId);
  }

  public Optional<Long> findJobRecordIdByTaskId(String taskId) {
    String sql = """
        select job_record_id
        from job_analysis
        where task_id = ?
        order by id desc
        limit 1
        """;
    List<Long> ids = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("job_record_id"), taskId);
    return ids.stream().findFirst();
  }

  private JobFeedbackResponse findById(Long id) {
    String sql = """
        select
          id,
          job_record_id,
          apply_status,
          chat_status,
          interview_status,
          feedback_note,
          reject_reason,
          created_at
        from job_feedback
        where id = ?
        """;
    return jdbcTemplate.queryForObject(sql, mapper(), id);
  }

  private RowMapper<JobFeedbackResponse> mapper() {
    return (rs, rowNum) -> {
      Timestamp createdAt = rs.getTimestamp("created_at");
      LocalDateTime createdAtValue = createdAt == null ? null : createdAt.toLocalDateTime();
      return new JobFeedbackResponse(
          rs.getLong("id"),
          rs.getLong("job_record_id"),
          rs.getString("apply_status"),
          rs.getString("chat_status"),
          rs.getString("interview_status"),
          rs.getString("feedback_note"),
          rs.getString("reject_reason"),
          createdAtValue
      );
    };
  }
}
