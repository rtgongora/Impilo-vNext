package zw.gov.mohcc.impilo.simba.connect;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Read-back for Health Connect parity (verify sync / power UI). */
@Service
public class WellnessHealthConnectQueryService {

    private final JdbcTemplate jdbc;

    public WellnessHealthConnectQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> sleepSegments(String tenantId, String patientId, int limit) {
        return jdbc.queryForList(
                """
                SELECT id, session_external_id, stage, start_at, end_at, created_at
                FROM wellness_sleep_segments
                WHERE tenant_id = ? AND patient_id = ?
                ORDER BY start_at DESC
                LIMIT ?
                """,
                tenantId, patientId, Math.min(Math.max(limit, 1), 500));
    }

    public List<Map<String, Object>> exerciseSessions(String tenantId, String patientId, int days) {
        return jdbc.queryForList(
                """
                SELECT id, external_record_id, exercise_type, title, start_at, end_at, energy_kcal, distance_m, created_at
                FROM wellness_exercise_sessions
                WHERE tenant_id = ? AND patient_id = ? AND start_at >= NOW() - (?::int * INTERVAL '1 day')
                ORDER BY start_at DESC
                LIMIT 500
                """,
                tenantId, patientId, days);
    }

    /** Dedupe log rows (optionally include JSON payload for replay). */
    public List<Map<String, Object>> ingestLog(String tenantId, String patientId, int limit, boolean includePayload) {
        int lim = Math.min(Math.max(limit, 1), 500);
        if (includePayload) {
            return jdbc.queryForList(
                    """
                    SELECT id, external_record_id, record_type, data_origin_platform, data_origin_package, ingested_at, payload
                    FROM wellness_connect_ingest_log
                    WHERE tenant_id = ? AND patient_id = ?
                    ORDER BY ingested_at DESC
                    LIMIT ?
                    """,
                    tenantId, patientId, lim);
        }
        return jdbc.queryForList(
                """
                SELECT id, external_record_id, record_type, data_origin_platform, data_origin_package, ingested_at
                FROM wellness_connect_ingest_log
                WHERE tenant_id = ? AND patient_id = ?
                ORDER BY ingested_at DESC
                LIMIT ?
                """,
                tenantId, patientId, lim);
    }

    /** Extension-store rows (unmapped / long-tail HC types). */
    public List<Map<String, Object>> extensionRecords(String tenantId, String patientId, int limit) {
        return jdbc.queryForList(
                """
                SELECT id, external_record_id, canonical_type, start_at, end_at, payload, created_at
                FROM wellness_connect_extension
                WHERE tenant_id = ? AND patient_id = ?
                ORDER BY created_at DESC
                LIMIT ?
                """,
                tenantId, patientId, Math.min(Math.max(limit, 1), 500));
    }
}
