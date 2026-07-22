package zw.gov.mohcc.impilo.rtc.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JDBC persistence for LiveKit webhook telemetry (rtc.session_events +
 * rtc.participant_stats + lifecycle columns on rtc.rtc_sessions).
 *
 * <p>Plain JDBC (not JPA) so the idempotent insert can use
 * {@code ON CONFLICT (lk_event_id) DO NOTHING} natively.
 */
@Component
public class RtcTelemetryPersistence {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public RtcTelemetryPersistence(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /**
     * Idempotent event insert keyed on the LiveKit event id.
     *
     * @return true when the row was inserted, false when this event id was already recorded.
     */
    public boolean insertEventIfNew(String sessionId, String lkEventId, String eventType,
                                    String participantIdentity, String roomName,
                                    JsonNode payload, Instant occurredAt) {
        int inserted = jdbc.update(
                "INSERT INTO rtc.session_events "
                        + "(session_id, lk_event_id, event_type, participant_identity, room_name, payload, occurred_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?) "
                        + "ON CONFLICT (lk_event_id) DO NOTHING",
                sessionId, lkEventId, eventType, participantIdentity, roomName,
                json(payload), timestamp(occurredAt));
        return inserted > 0;
    }

    /**
     * TM-B19: session-diagnostics READ path over {@code rtc.session_events} (write-only until now).
     * Returns transport/media telemetry ONLY — the table structurally contains no clinical content
     * (event type, participant identity, room, raw LiveKit payload), which is what makes the
     * helpdesk surface safe by construction.
     */
    public java.util.List<java.util.Map<String, Object>> findSessionEvents(String sessionId, int limit) {
        int capped = Math.max(1, Math.min(limit, 500));
        return jdbc.query(
                "SELECT event_type, participant_identity, room_name, occurred_at "
                        + "FROM rtc.session_events WHERE session_id = ? "
                        + "ORDER BY occurred_at ASC LIMIT " + capped,
                (rs, i) -> {
                    java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("eventType", rs.getString("event_type"));
                    row.put("participantIdentity", rs.getString("participant_identity"));
                    row.put("roomName", rs.getString("room_name"));
                    java.sql.Timestamp at = rs.getTimestamp("occurred_at");
                    row.put("occurredAt", at == null ? null : at.toInstant().toString());
                    return row;
                },
                sessionId);
    }

    public void markSessionStarted(String sessionId, Instant startedAt) {
        jdbc.update("UPDATE rtc.rtc_sessions SET started_at = COALESCE(started_at, ?), updated_at = now() WHERE id = ?",
                timestamp(startedAt), sessionId);
    }

    public void markSessionEnded(String sessionId, Instant endedAt) {
        jdbc.update("UPDATE rtc.rtc_sessions SET ended_at = ?, updated_at = now() WHERE id = ?",
                timestamp(endedAt), sessionId);
    }

    /** Open a participant-stats row on participant_joined and refresh the session's peak. */
    public void recordParticipantJoined(String sessionId, String identity, Instant joinedAt, JsonNode payload) {
        jdbc.update(
                "INSERT INTO rtc.participant_stats (session_id, identity, joined_at, payload) "
                        + "VALUES (?, ?, ?, ?::jsonb)",
                sessionId, identity, timestamp(joinedAt), json(payload));
        jdbc.update(
                "UPDATE rtc.rtc_sessions SET peak_participants = GREATEST(COALESCE(peak_participants, 0), "
                        + "(SELECT COUNT(*) FROM rtc.participant_stats WHERE session_id = ? AND left_at IS NULL)) "
                        + "WHERE id = ?",
                sessionId, sessionId);
    }

    /**
     * Close the most recent open stats row for this participant, computing duration from its
     * joined_at; falls back to inserting a left-only row when no join was observed.
     */
    public void recordParticipantLeft(String sessionId, String identity, Instant leftAt,
                                      String disconnectReason, JsonNode payload) {
        int updated = jdbc.update(
                "UPDATE rtc.participant_stats SET left_at = ?, "
                        + "duration_seconds = GREATEST(0, CAST(EXTRACT(EPOCH FROM (?::timestamptz - joined_at)) AS INT)), "
                        + "disconnect_reason = ?, payload = ?::jsonb "
                        + "WHERE id = (SELECT id FROM rtc.participant_stats "
                        + "  WHERE session_id = ? AND identity = ? AND left_at IS NULL "
                        + "  ORDER BY joined_at DESC NULLS LAST, id DESC LIMIT 1)",
                timestamp(leftAt), timestamp(leftAt), disconnectReason, json(payload), sessionId, identity);
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO rtc.participant_stats (session_id, identity, left_at, disconnect_reason, payload) "
                            + "VALUES (?, ?, ?, ?, ?::jsonb)",
                    sessionId, identity, timestamp(leftAt), disconnectReason, json(payload));
        }
    }

    /** One participant_stats interval, as consumed by the session stats aggregation. */
    public record ParticipantStatRow(
            String identity,
            Instant joinedAt,
            Instant leftAt,
            Integer durationSeconds,
            String connectionQuality,
            String disconnectReason
    ) {
    }

    /** Session lifecycle columns maintained by the webhook translator. */
    public record SessionLifecycle(Instant startedAt, Instant endedAt, Integer peakParticipants) {
    }

    /** All participant intervals observed for a session (webhook media truth). */
    public List<ParticipantStatRow> findParticipantStats(String sessionId) {
        return jdbc.query(
                "SELECT identity, joined_at, left_at, duration_seconds, connection_quality, disconnect_reason "
                        + "FROM rtc.participant_stats WHERE session_id = ? ORDER BY joined_at NULLS LAST, id",
                (rs, i) -> new ParticipantStatRow(
                        rs.getString("identity"),
                        instant(rs.getTimestamp("joined_at")),
                        instant(rs.getTimestamp("left_at")),
                        (Integer) rs.getObject("duration_seconds"),
                        rs.getString("connection_quality"),
                        rs.getString("disconnect_reason")),
                sessionId);
    }

    public Optional<SessionLifecycle> findSessionLifecycle(String sessionId) {
        return jdbc.query(
                "SELECT started_at, ended_at, peak_participants FROM rtc.rtc_sessions WHERE id = ?",
                (rs, i) -> new SessionLifecycle(
                        instant(rs.getTimestamp("started_at")),
                        instant(rs.getTimestamp("ended_at")),
                        (Integer) rs.getObject("peak_participants")),
                sessionId).stream().findFirst();
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private String json(JsonNode payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unserializable webhook payload", e);
        }
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
