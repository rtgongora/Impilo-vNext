package zw.gov.mohcc.impilo.rtc.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.rtc.model.RtcParticipantRecord;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JDBC persistence for rtc.session_participants (waiting-room lobby state).
 *
 * <p>Plain JDBC like {@link RtcTelemetryPersistence} so the upsert can use
 * {@code ON CONFLICT (session_id, identity)} natively — concurrent token
 * polls from the same waiting participant must not race into duplicates.
 */
@Component
public class JdbcRtcParticipantPersistence implements RtcParticipantPersistence {

    private static final RowMapper<RtcParticipantRecord> MAPPER = JdbcRtcParticipantPersistence::mapRow;

    private final JdbcTemplate jdbc;

    public JdbcRtcParticipantPersistence(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public RtcParticipantRecord upsert(RtcParticipantRecord p) {
        jdbc.update(
                "INSERT INTO rtc.session_participants "
                        + "(session_id, identity, display_name, role, state, media_profile, "
                        + " requested_at, admitted_at, admitted_by, denied_reason) "
                        + "VALUES (?, ?, ?, ?, ?, ?, COALESCE(?, now()), ?, ?, ?) "
                        + "ON CONFLICT (session_id, identity) DO UPDATE SET "
                        + "  display_name = COALESCE(EXCLUDED.display_name, rtc.session_participants.display_name), "
                        + "  role = COALESCE(EXCLUDED.role, rtc.session_participants.role), "
                        + "  state = EXCLUDED.state, "
                        + "  media_profile = COALESCE(EXCLUDED.media_profile, rtc.session_participants.media_profile), "
                        + "  admitted_at = EXCLUDED.admitted_at, "
                        + "  admitted_by = EXCLUDED.admitted_by, "
                        + "  denied_reason = EXCLUDED.denied_reason",
                p.sessionId(), p.identity(), p.displayName(), p.role(), p.state(), p.mediaProfile(),
                timestamp(p.requestedAt()), timestamp(p.admittedAt()), p.admittedBy(), p.deniedReason());
        return find(p.sessionId(), p.identity()).orElse(p);
    }

    @Override
    public Optional<RtcParticipantRecord> find(String sessionId, String identity) {
        List<RtcParticipantRecord> rows = jdbc.query(
                "SELECT * FROM rtc.session_participants WHERE session_id = ? AND identity = ?",
                MAPPER, sessionId, identity);
        return rows.stream().findFirst();
    }

    @Override
    public List<RtcParticipantRecord> findBySession(String sessionId) {
        return jdbc.query(
                "SELECT * FROM rtc.session_participants WHERE session_id = ? ORDER BY requested_at, id",
                MAPPER, sessionId);
    }

    @Override
    public void updateState(String sessionId, String identity, String state) {
        jdbc.update("UPDATE rtc.session_participants SET state = ? WHERE session_id = ? AND identity = ?",
                state, sessionId, identity);
    }

    private static RtcParticipantRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new RtcParticipantRecord(
                rs.getString("session_id"),
                rs.getString("identity"),
                rs.getString("display_name"),
                rs.getString("role"),
                rs.getString("state"),
                rs.getString("media_profile"),
                instant(rs.getTimestamp("requested_at")),
                instant(rs.getTimestamp("admitted_at")),
                rs.getString("admitted_by"),
                rs.getString("denied_reason"));
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
