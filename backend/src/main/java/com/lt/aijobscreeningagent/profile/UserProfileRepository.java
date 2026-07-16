package com.lt.aijobscreeningagent.profile;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import com.lt.aijobscreeningagent.resume.ResumeProfileExtraction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserProfileRepository {

  private static final String DEFAULT_PROFILE_NAME = "default";

  private final JdbcTemplate jdbcTemplate;

  public UserProfileRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UserProfile saveDefault(UserProfileRequest request) {
    String sql = """
        insert into user_profile (
          profile_name,
          target_roles,
          preferred_cities,
          skills,
          projects,
          positive_keywords,
          negative_keywords,
          hard_reject_keywords,
          schedule_preference,
          manual_text,
          created_at,
          updated_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
        on duplicate key update
          target_roles = values(target_roles),
          preferred_cities = values(preferred_cities),
          skills = values(skills),
          projects = values(projects),
          positive_keywords = values(positive_keywords),
          negative_keywords = values(negative_keywords),
          hard_reject_keywords = values(hard_reject_keywords),
          schedule_preference = values(schedule_preference),
          manual_text = values(manual_text),
          updated_at = now()
        """;

    jdbcTemplate.update(
        sql,
        DEFAULT_PROFILE_NAME,
        normalize(request.targetRoles()),
        normalize(request.preferredCities()),
        normalize(request.skills()),
        normalize(request.projects()),
        normalize(request.positiveKeywords()),
        normalize(request.negativeKeywords()),
        normalize(request.hardRejectKeywords()),
        normalize(request.schedulePreference()),
        normalize(request.manualText())
    );

    return findDefault().orElseThrow(() -> new IllegalStateException("Failed to save default user profile"));
  }

  public Optional<UserProfile> findDefault() {
    String sql = """
        select
          id,
          profile_name,
          target_roles,
          preferred_cities,
          skills,
          projects,
          positive_keywords,
          negative_keywords,
          hard_reject_keywords,
          schedule_preference,
          experience,
          education,
          resume_text,
          manual_text,
          created_at,
          updated_at
        from user_profile
        where profile_name = ?
        limit 1
        """;
    List<UserProfile> profiles = jdbcTemplate.query(sql, mapper(), DEFAULT_PROFILE_NAME);
    return profiles.stream().findFirst();
  }

  private RowMapper<UserProfile> mapper() {
    return (rs, rowNum) -> new UserProfile(
        rs.getLong("id"),
        rs.getString("profile_name"),
        rs.getString("target_roles"),
        rs.getString("preferred_cities"),
        rs.getString("skills"),
        rs.getString("projects"),
        rs.getString("positive_keywords"),
        rs.getString("negative_keywords"),
        rs.getString("hard_reject_keywords"),
        rs.getString("schedule_preference"),
        rs.getString("experience"),
        rs.getString("education"),
        rs.getString("resume_text"),
        rs.getString("manual_text"),
        toLocalDateTime(rs.getTimestamp("created_at")),
        toLocalDateTime(rs.getTimestamp("updated_at"))
    );
  }

  public UserProfile saveResumeProfile(ResumeProfileExtraction extraction, String resumeText) {
    String sql = """
        insert into user_profile (
          profile_name,
          target_roles,
          skills,
          projects,
          positive_keywords,
          experience,
          education,
          resume_text,
          created_at,
          updated_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, now(), now())
        on duplicate key update
          target_roles = values(target_roles),
          skills = values(skills),
          projects = values(projects),
          positive_keywords = values(positive_keywords),
          experience = values(experience),
          education = values(education),
          resume_text = values(resume_text),
          updated_at = now()
        """;
    jdbcTemplate.update(
        sql,
        DEFAULT_PROFILE_NAME,
        join(extraction.targetRoles()),
        join(extraction.skills()),
        join(extraction.projects()),
        join(extraction.keywords()),
        normalize(extraction.experience()),
        normalize(extraction.education()),
        normalize(resumeText)
    );
    return findDefault().orElseThrow(() -> new IllegalStateException("Failed to save resume profile"));
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  private String join(List<String> values) {
    if (values == null) {
      return "";
    }
    return values.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(String::trim)
        .distinct()
        .reduce((left, right) -> left + "、" + right)
        .orElse("");
  }

  private LocalDateTime toLocalDateTime(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toLocalDateTime();
  }
}
