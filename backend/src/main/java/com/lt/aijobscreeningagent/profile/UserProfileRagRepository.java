package com.lt.aijobscreeningagent.profile;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class UserProfileRagRepository {

  private static final String DEFAULT_PROFILE_NAME = "default";

  private final JdbcTemplate jdbcTemplate;

  public UserProfileRagRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void deleteDefaultProfileIndex() {
    jdbcTemplate.update("""
        delete c from user_profile_chunk c
        join user_profile_document d on c.document_id = d.id
        where d.profile_name = ?
        """, DEFAULT_PROFILE_NAME);
    jdbcTemplate.update("delete from user_profile_document where profile_name = ?", DEFAULT_PROFILE_NAME);
  }

  public Long saveDocument(String rawText, String contentHash) {
    String sql = """
        insert into user_profile_document (
          profile_name,
          doc_type,
          doc_name,
          source_type,
          raw_text,
          content_hash,
          created_at,
          updated_at
        ) values (?, ?, ?, ?, ?, ?, now(), now())
        """;

    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, DEFAULT_PROFILE_NAME);
      ps.setString(2, "manual_profile");
      ps.setString(3, "default user profile");
      ps.setString(4, "manual_input");
      ps.setString(5, rawText);
      ps.setString(6, contentHash);
      return ps;
    }, keyHolder);

    Number key = keyHolder.getKey();
    if (key == null) {
      throw new IllegalStateException("Failed to get generated user_profile_document id");
    }
    return key.longValue();
  }

  public void saveChunk(Long documentId, int chunkIndex, String title, String content, String contentHash, int scoreHint) {
    jdbcTemplate.update("""
        insert into user_profile_chunk (
          profile_name,
          document_id,
          chunk_index,
          title,
          content,
          source_type,
          content_hash,
          score_hint,
          created_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, now())
        """,
        DEFAULT_PROFILE_NAME,
        documentId,
        chunkIndex,
        title,
        content,
        "manual_profile",
        contentHash,
        scoreHint
    );
  }

  public List<UserProfileChunk> findDefaultChunks() {
    return jdbcTemplate.query("""
        select
          id,
          profile_name,
          document_id,
          chunk_index,
          title,
          content,
          source_type,
          content_hash,
          score_hint,
          created_at
        from user_profile_chunk
        where profile_name = ?
        order by chunk_index asc, id asc
        """, mapper(), DEFAULT_PROFILE_NAME);
  }

  private RowMapper<UserProfileChunk> mapper() {
    return (rs, rowNum) -> new UserProfileChunk(
        rs.getLong("id"),
        rs.getString("profile_name"),
        rs.getLong("document_id"),
        rs.getInt("chunk_index"),
        rs.getString("title"),
        rs.getString("content"),
        rs.getString("source_type"),
        rs.getString("content_hash"),
        rs.getInt("score_hint"),
        toLocalDateTime(rs.getTimestamp("created_at"))
    );
  }

  private LocalDateTime toLocalDateTime(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toLocalDateTime();
  }
}
