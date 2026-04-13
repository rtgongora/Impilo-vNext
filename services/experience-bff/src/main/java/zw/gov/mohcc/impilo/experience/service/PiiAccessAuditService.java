package zw.gov.mohcc.impilo.experience.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.sql.Array;
import java.time.OffsetDateTime;

/**
 * Records every access to PII fields in an append-only audit log.
 *
 * <p>Per Health OS doctrine and Privacy Policy §14: audit logging is a
 * core security safeguard. Every view, search, export, print, or copy
 * of PII is recorded with who accessed it, whose data it was, what
 * fields were accessed, and the purpose of use.</p>
 *
 * <p>Writes are async to avoid blocking the request path. The audit
 * log is append-only and immutable — no UPDATE or DELETE operations.</p>
 */
@Service
public class PiiAccessAuditService {

    private static final Logger log = LoggerFactory.getLogger(PiiAccessAuditService.class);

    private final JdbcTemplate jdbcTemplate;

    public PiiAccessAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Record a PII access event asynchronously.
     *
     * @param tenantId       Tenant context
     * @param actorId        Who accessed the PII (Health ID)
     * @param actorType      PROVIDER, OPERATOR, CITIZEN, SYSTEM
     * @param subjectId      Whose PII was accessed (patient/user ID)
     * @param subjectType    PATIENT, USER, PROVIDER
     * @param action         VIEW, SEARCH, EXPORT, PRINT, COPY
     * @param fieldsAccessed Array of field names accessed (e.g. "name", "dob", "national_id")
     * @param purposeOfUse   TREATMENT, PAYMENT, OPERATIONS, PUBLIC_HEALTH, EMERGENCY
     * @param accessChannel  WEB, APP, API, KIOSK
     * @param privacyLevel   FULL, PARTIAL, MINIMAL — the display level used
     * @param ipAddress      Client IP
     * @param userAgent      Client user-agent
     * @param facilityId     Facility context
     */
    @Async
    public void recordAccess(
            String tenantId,
            String actorId,
            String actorType,
            String subjectId,
            String subjectType,
            String action,
            String[] fieldsAccessed,
            String purposeOfUse,
            String accessChannel,
            String privacyLevel,
            String ipAddress,
            String userAgent,
            String facilityId) {

        try {
            Array fieldsArray = jdbcTemplate.getDataSource() != null
                    ? jdbcTemplate.getDataSource().getConnection().createArrayOf("text", fieldsAccessed)
                    : null;

            jdbcTemplate.update(
                    "INSERT INTO pii_access_log (tenant_id, actor_id, actor_type, subject_id, " +
                    "subject_type, action, fields_accessed, purpose_of_use, access_channel, " +
                    "privacy_level, ip_address, user_agent, facility_id, occurred_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    tenantId, actorId, actorType, subjectId, subjectType, action,
                    fieldsArray, purposeOfUse, accessChannel, privacyLevel,
                    ipAddress, userAgent, facilityId, OffsetDateTime.now());
        } catch (Exception e) {
            // Audit failures must never break the request — log and continue
            log.warn("Failed to record PII access audit: actor={}, subject={}, action={}: {}",
                    actorId, subjectId, action, e.getMessage());
        }
    }
}
