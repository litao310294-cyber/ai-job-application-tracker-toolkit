package com.lt.aijobscreeningagent.profile;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserScoringConfigRepository {

  private static final String DEFAULT_PROFILE_NAME = "default";

  private final JdbcTemplate jdbcTemplate;

  public UserScoringConfigRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UserScoringConfig saveDefault(String configJson, String generatedBy, boolean confirmed) {
    String sql = """
        insert into user_scoring_config (
          profile_name,
          config_json,
          generated_by,
          confirmed,
          created_at,
          updated_at
        ) values (?, ?, ?, ?, now(), now())
        on duplicate key update
          config_json = values(config_json),
          generated_by = values(generated_by),
          confirmed = values(confirmed),
          updated_at = now()
        """;
    jdbcTemplate.update(sql, DEFAULT_PROFILE_NAME, configJson, generatedBy, confirmed ? 1 : 0);
    return findDefault().orElseThrow(() -> new IllegalStateException("Failed to save default scoring config"));
  }

  public Optional<UserScoringConfig> findDefault() {
    String sql = """
        select
          id,
          profile_name,
          config_json,
          generated_by,
          confirmed,
          created_at,
          updated_at
        from user_scoring_config
        where profile_name = ?
        limit 1
        """;
    List<UserScoringConfig> configs = jdbcTemplate.query(sql, mapper(), DEFAULT_PROFILE_NAME);
    return configs.stream().findFirst();
  }

  public Optional<UserScoringConfig> confirmDefault() {
    int updated = jdbcTemplate.update(
        "update user_scoring_config set confirmed = 1, updated_at = now() where profile_name = ?",
        DEFAULT_PROFILE_NAME
    );
    if (updated == 0) {
      return Optional.empty();
    }
    return findDefault();
  }

  private RowMapper<UserScoringConfig> mapper() {
    return (rs, rowNum) -> new UserScoringConfig(
        rs.getLong("id"),
        rs.getString("profile_name"),
        rs.getString("config_json"),
        rs.getString("generated_by"),
        rs.getInt("confirmed") == 1,
        toLocalDateTime(rs.getTimestamp("created_at")),
        toLocalDateTime(rs.getTimestamp("updated_at"))
    );
  }

  private LocalDateTime toLocalDateTime(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toLocalDateTime();
  }
}
