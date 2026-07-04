package zw.gov.mohcc.impilo.rtc.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

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
