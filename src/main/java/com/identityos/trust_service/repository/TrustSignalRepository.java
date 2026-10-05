package com.identityos.trust_service.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.identityos.trust_service.dto.TrustScoreResponse;
import com.identityos.trust_service.dto.TrustSignalRequest;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TrustSignalRepository {
  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public TrustSignalRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void saveSignal(TrustSignalRequest request) {
    jdbcTemplate.update(
        """
        insert into trust_signal_event (
          subject_type, subject_id, organization_id, application_id, session_id,
          signal_type, outcome, weight, source, metadata, occurred_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
        """,
        normalize(request.subjectType()),
        request.subjectId(),
        request.organizationId(),
        request.applicationId(),
        request.sessionId(),
        normalize(request.signalType()),
        normalize(request.outcome()),
        request.weight(),
        request.source(),
        json(request.metadata() == null ? Map.of() : request.metadata()),
        Timestamp.from((request.occurredAt() == null ? OffsetDateTime.now() : request.occurredAt()).toInstant()));
  }

  public List<Map<String, Object>> findSignals(
      String subjectType, String subjectId, String applicationId, int limit) {
    if (applicationId == null || applicationId.isBlank()) {
      return jdbcTemplate.queryForList(
          """
          select * from trust_signal_event
          where subject_type = ? and subject_id = ?
          order by occurred_at desc
          limit ?
          """,
          normalize(subjectType),
          subjectId,
          limit);
    }
    return jdbcTemplate.queryForList(
        """
        select * from trust_signal_event
        where subject_type = ? and subject_id = ? and application_id = ?
        order by occurred_at desc
        limit ?
        """,
        normalize(subjectType),
        subjectId,
        applicationId,
        limit);
  }

  public List<Map<String, Object>> recentEvents(String subjectId, int limit) {
    if (subjectId == null || subjectId.isBlank()) {
      return jdbcTemplate.queryForList(
          "select * from trust_signal_event order by occurred_at desc limit ?", limit);
    }
    return jdbcTemplate.queryForList(
        """
        select * from trust_signal_event
        where subject_id = ?
        order by occurred_at desc
        limit ?
        """,
        subjectId,
        limit);
  }

  public void saveSnapshot(TrustScoreResponse response) {
    String reasons = json(response.reasons());
    jdbcTemplate.update(
        """
        insert into trust_score_snapshot (
          subject_type, subject_id, organization_id, application_id, identity_score,
          behaviour_score, risk_penalty, final_score, trust_level, decision, reasons
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
        """,
        normalize(response.subjectType()),
        response.subjectId(),
        response.organizationId(),
        response.applicationId(),
        response.identityScore(),
        response.behaviourScore(),
        response.riskPenalty(),
        response.finalScore(),
        response.trustLevel(),
        response.decision(),
        reasons);

    jdbcTemplate.update(
        """
        insert into trust_decision_history (
          subject_type, subject_id, organization_id, application_id, decision, final_score, reasons
        ) values (?, ?, ?, ?, ?, ?, ?::jsonb)
        """,
        normalize(response.subjectType()),
        response.subjectId(),
        response.organizationId(),
        response.applicationId(),
        response.decision(),
        response.finalScore(),
        reasons);
  }

  public List<Map<String, Object>> recentDecisions(String subjectId, int limit) {
    if (subjectId == null || subjectId.isBlank()) {
      return jdbcTemplate.queryForList(
          "select * from trust_decision_history order by created_at desc limit ?", limit);
    }
    return jdbcTemplate.queryForList(
        """
        select * from trust_decision_history
        where subject_id = ?
        order by created_at desc
        limit ?
        """,
        subjectId,
        limit);
  }

  public OffsetDateTime utcNow() {
    return OffsetDateTime.now(ZoneOffset.UTC);
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Unable to serialize trust data.", exception);
    }
  }

  private String normalize(String value) {
    return value == null ? null : value.trim().toUpperCase();
  }
}
