package zw.gov.mohcc.impilo.simba.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Writes Simba wellness access-audit rows for sensitive, cross-person, coaching, programme,
 * and care-routing actions. Mirrors the JDBC audit pattern already used by
 * {@code PersonalHealthDataController}, but uses the general {@code simba.wellness_access_audit}
 * trail (actor ↔ subject ↔ relationship).
 */
@Service
public class WellnessAuditService {

    private static final Logger log = LoggerFactory.getLogger(WellnessAuditService.class);

    private final JdbcTemplate jdbc;

    public WellnessAuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Relationship of the actor to the subject of an action. */
    public static final String SELF = "SELF";
    public static final String DEPENDANT = "DEPENDANT";
    public static final String COACH = "COACH";
    public static final String PROGRAMME_ADMIN = "PROGRAMME_ADMIN";

    public void record(
            UUID tenantId,
            String actorId,
            String actorType,
            String providerId,
            String subjectCpid,
            String relationship,
            String facilityId,
            String workspaceId,
            String action,
            String decision,
            String reason,
            String correlationId,
            String requestId) {
        // Audit is best-effort: a transient audit-write failure must never block a wellness action.
        try {
            jdbc.update(
                    """
                    INSERT INTO simba.wellness_access_audit (
                        audit_id, tenant_id, actor_id, actor_type, provider_id, subject_cpid, relationship,
                        facility_id, workspace_id, action, decision, reason, correlation_id, request_id
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(), tenantId, actorId, actorType, providerId, subjectCpid, relationship,
                    facilityId, workspaceId, action, decision == null ? "ALLOW" : decision, reason,
                    correlationId, requestId);
        } catch (RuntimeException ex) {
            log.warn("Wellness audit write failed for action={} subject={}: {}",
                    action, subjectCpid, ex.getMessage());
        }
    }

    /** Convenience overload for self-service ALLOW audit with minimal context. */
    public void recordSelf(UUID tenantId, String actorId, String actorType, String subjectCpid,
                           String action, String correlationId, String requestId) {
        record(tenantId, actorId, actorType, null, subjectCpid, SELF, null, null,
                action, "ALLOW", null, correlationId, requestId);
    }
}
