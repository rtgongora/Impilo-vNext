package zw.gov.mohcc.impilo.rtc.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.rtc.model.RtcRecordingRecord;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JDBC persistence for rtc.recordings (one row per egress lifecycle).
 *
 * <p>Plain JDBC like {@link RtcTelemetryPersistence} so webhook-side writes can
 * upsert natively on egress_id ({@code ON CONFLICT}) — the egress_started
 * webhook can race the StartRoomCompositeEgress response commit.
 */
@Component
public class RtcRecordingPersistence {

    private static final RowMapper<RtcRecordingRecord> MAPPER = RtcRecordingPersistence::mapRow;

    private final JdbcTemplate jdbc;

    public RtcRecordingPersistence(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertRequested(String sessionId, String egressId, String layout,
                                String storageBucket, String storageKey,
                                String startedBy, String startedByRole, String consentReference) {
        jdbc.update(
                "INSERT INTO rtc.recordings "
                        + "(session_id, egress_id, status, layout, storage_bucket, storage_key, "
                        + " started_by, started_by_role, consent_reference) "
                        + "VALUES (?, ?, 'REQUESTED', ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (egress_id) DO UPDATE SET "
                        + "  layout = EXCLUDED.layout, "
                        + "  storage_bucket = EXCLUDED.storage_bucket, "
                        + "  storage_key = COALESCE(rtc.recordings.storage_key, EXCLUDED.storage_key), "
                        + "  started_by = EXCLUDED.started_by, "
                        + "  started_by_role = EXCLUDED.started_by_role, "
                        + "  consent_reference = EXCLUDED.consent_reference",
                sessionId, egressId, layout, storageBucket, storageKey,
                startedBy, startedByRole, consentReference);
    }

    /** egress_started: mark ACTIVE; upsert when the webhook raced the start() commit. */
    public void markActive(String sessionId, String egressId) {
        jdbc.update(
                "INSERT INTO rtc.recordings (session_id, egress_id, status) "
                        + "VALUES (?, ?, 'ACTIVE') "
                        + "ON CONFLICT (egress_id) DO UPDATE SET status = 'ACTIVE' "
                        + "WHERE rtc.recordings.status IN ('REQUESTED', 'ACTIVE')",
                sessionId, egressId);
    }

    /** egress_updated: refresh size/duration when LiveKit reports progress. */
    public void updateProgress(String egressId, Long sizeBytes, Integer durationSeconds) {
        jdbc.update(
                "UPDATE rtc.recordings SET "
                        + "size_bytes = COALESCE(?, size_bytes), "
                        + "duration_seconds = COALESCE(?, duration_seconds) "
                        + "WHERE egress_id = ?",
                sizeBytes, durationSeconds, egressId);
    }

    /** egress_ended: terminal status + artifact location; upsert for unseen egress ids. */
    public void markEnded(String sessionId, String egressId, String status,
                          String storageBucket, String storageKey,
                          Long sizeBytes, Integer durationSeconds, Instant completedAt) {
        jdbc.update(
                "INSERT INTO rtc.recordings "
                        + "(session_id, egress_id, status, storage_bucket, storage_key, size_bytes, duration_seconds, completed_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (egress_id) DO UPDATE SET "
                        + "  status = EXCLUDED.status, "
                        + "  storage_bucket = COALESCE(EXCLUDED.storage_bucket, rtc.recordings.storage_bucket), "
                        + "  storage_key = COALESCE(EXCLUDED.storage_key, rtc.recordings.storage_key), "
                        + "  size_bytes = COALESCE(EXCLUDED.size_bytes, rtc.recordings.size_bytes), "
                        + "  duration_seconds = COALESCE(EXCLUDED.duration_seconds, rtc.recordings.duration_seconds), "
                        + "  completed_at = EXCLUDED.completed_at",
                sessionId, egressId, status, storageBucket, storageKey,
                sizeBytes, durationSeconds, timestamp(completedAt));
    }

    public void markStopped(String egressId) {
        jdbc.update("UPDATE rtc.recordings SET status = 'STOPPED', completed_at = now() "
                + "WHERE egress_id = ? AND status IN ('REQUESTED', 'ACTIVE')", egressId);
    }

    /** One recording at a time per session. */
    public boolean hasInFlight(String sessionId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rtc.recordings WHERE session_id = ? AND status IN ('REQUESTED', 'ACTIVE')",
                Integer.class, sessionId);
        return count != null && count > 0;
    }

    public Optional<RtcRecordingRecord> findInFlight(String sessionId) {
        List<RtcRecordingRecord> rows = jdbc.query(
                "SELECT * FROM rtc.recordings WHERE session_id = ? AND status IN ('REQUESTED', 'ACTIVE') "
                        + "ORDER BY created_at DESC, id DESC LIMIT 1",
                MAPPER, sessionId);
        return rows.stream().findFirst();
    }

    public Optional<RtcRecordingRecord> findByEgressId(String egressId) {
        List<RtcRecordingRecord> rows = jdbc.query(
                "SELECT * FROM rtc.recordings WHERE egress_id = ?", MAPPER, egressId);
        return rows.stream().findFirst();
    }

    public List<RtcRecordingRecord> findBySessionId(String sessionId) {
        return jdbc.query(
                "SELECT * FROM rtc.recordings WHERE session_id = ? ORDER BY created_at DESC, id DESC",
                MAPPER, sessionId);
    }

    private static RtcRecordingRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new RtcRecordingRecord(
                rs.getLong("id"),
                rs.getString("session_id"),
                rs.getString("egress_id"),
                rs.getString("status"),
                rs.getString("layout"),
                rs.getString("storage_bucket"),
                rs.getString("storage_key"),
                rs.getString("document_object_id"),
                (Integer) rs.getObject("duration_seconds"),
                (Long) rs.getObject("size_bytes"),
                rs.getString("started_by"),
                rs.getString("started_by_role"),
                rs.getString("consent_reference"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("completed_at")));
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
