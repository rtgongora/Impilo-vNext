package zw.gov.mohcc.impilo.simba.core;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Above-site wellness aggregate metrics. Produces ONLY counts/rates — never client-level rows, never
 * a person_cpid. Scoped by tenant (facility/programme scoping is applied by the caller via the guard).
 * Uses JdbcTemplate so the aggregation stays a single set-based query per metric.
 */
@Service
public class WellnessAggregateService {

    private final JdbcTemplate jdbc;

    public WellnessAggregateService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(UUID tenantId) {
        Map<String, Object> out = new LinkedHashMap<>();

        // Enrolment: total active enrolments + distinct enrolled persons.
        out.put("active_enrolments", count(
                "SELECT COUNT(*) FROM simba.plan_enrollments WHERE tenant_id = ? AND status = 'ACTIVE'", tenantId));
        out.put("enrolled_persons", count(
                "SELECT COUNT(DISTINCT person_cpid) FROM simba.plan_enrollments WHERE tenant_id = ?", tenantId));

        // Assessments completed + high-risk escalations (band-level, no cpid).
        out.put("assessments_completed", count(
                "SELECT COUNT(*) FROM simba.simba_assessment WHERE tenant_id = ? AND status = 'COMPLETED'", tenantId));
        out.put("high_risk_escalations", count(
                """
                SELECT COUNT(*) FROM simba.simba_assessment
                WHERE tenant_id = ? AND status = 'COMPLETED'
                  AND risk_band IN ('HIGH','NEEDS_CLINICAL_REVIEW','URGENT_RED_FLAG')
                """, tenantId));

        // Screening/reminder coverage (proxy: scheduled screening reminders vs completed).
        long screeningScheduled = count(
                "SELECT COUNT(*) FROM simba.simba_preventive_reminder WHERE tenant_id = ? AND reminder_type = 'SCREENING'",
                tenantId);
        long screeningCompleted = count(
                """
                SELECT COUNT(*) FROM simba.simba_preventive_reminder
                WHERE tenant_id = ? AND reminder_type = 'SCREENING' AND status = 'COMPLETED'
                """, tenantId);
        out.put("screening_reminders_scheduled", screeningScheduled);
        out.put("screening_coverage_pct", pct(screeningCompleted, screeningScheduled));

        // Follow-up completion.
        long followOpen = count(
                "SELECT COUNT(*) FROM simba.simba_follow_up_action WHERE tenant_id = ? AND status IN ('OPEN','IN_PROGRESS')",
                tenantId);
        long followDone = count(
                "SELECT COUNT(*) FROM simba.simba_follow_up_action WHERE tenant_id = ? AND status = 'DONE'",
                tenantId);
        out.put("follow_ups_open", followOpen);
        out.put("follow_up_completion_pct", pct(followDone, followDone + followOpen));

        // Care linkages routed (band → care).
        out.put("care_linkages_routed", count(
                "SELECT COUNT(*) FROM simba.care_linkages WHERE tenant_id = ?", tenantId));

        return out;
    }

    private long count(String sql, UUID tenantId) {
        Long v = jdbc.queryForObject(sql, Long.class, tenantId);
        return v == null ? 0L : v;
    }

    private static int pct(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return (int) Math.round((numerator * 100.0) / denominator);
    }
}
