package com.lt.aijobscreeningagent.service.trace;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JobAnalysisTraceRepository {

  private final JdbcTemplate jdbcTemplate;

  public JobAnalysisTraceRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public long insertStart(String taskId, Long jobRecordId, String stage, String inputData) {
    String sql = """
        insert into job_analysis_trace (
          task_id, job_record_id, stage, input_data, created_time
        ) values (?, ?, ?, ?, now())
        """;
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
      statement.setString(1, taskId);
      if (jobRecordId == null) {
        statement.setObject(2, null);
      } else {
        statement.setLong(2, jobRecordId);
      }
      statement.setString(3, stage);
      statement.setString(4, inputData);
      return statement;
    }, keyHolder);
    Number key = keyHolder.getKey();
    if (key == null) {
      throw new IllegalStateException("Failed to get generated job_analysis_trace id");
    }
    return key.longValue();
  }

  public void finish(long traceId, String outputData, long latencyMs) {
    jdbcTemplate.update(
        "update job_analysis_trace set output_data = ?, latency_ms = ? where id = ?",
        outputData, latencyMs, traceId);
  }

  public List<JobAnalysisTrace> findByTaskId(String taskId) {
    return jdbcTemplate.query("""
        select id, task_id, job_record_id, stage, input_data, output_data, latency_ms, created_time
        from job_analysis_trace
        where task_id = ?
        order by id asc
        """, (rs, rowNum) -> new JobAnalysisTrace(
        rs.getLong("id"),
        rs.getString("task_id"),
        (Long) rs.getObject("job_record_id"),
        rs.getString("stage"),
        rs.getString("input_data"),
        rs.getString("output_data"),
        (Long) rs.getObject("latency_ms"),
        toLocalDateTime(rs.getTimestamp("created_time"))
    ), taskId);
  }

  private LocalDateTime toLocalDateTime(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toLocalDateTime();
  }
}
