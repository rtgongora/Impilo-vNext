package zw.gov.mohcc.impilo.experience.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

import static zw.gov.mohcc.impilo.experience.events.EventPayloadExtractor.*;

/**
 * Consumes VITO identity events and upserts into the BFF-local {@code patients} table.
 *
 * <p>Topics consumed (DUAL emit mode → both arrive per event):</p>
 * <ul>
 *   <li>{@code vito.identity}        — legacy raw payload</li>
 *   <li>{@code kernel.vito.identity} — v1.1 EventEnvelope</li>
 * </ul>
 *
 * <p>Handles IDENTITY_CREATED, IDENTITY_VERIFIED, IDENTITY_DECEASED events.
 * Uses PostgreSQL {@code ON CONFLICT (tenant_id, cpid) DO UPDATE} for idempotency.</p>
 */
@Component
public class PatientEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PatientEventConsumer.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PatientEventConsumer(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {"vito.identity", "kernel.vito.identity"},
            groupId = "experience-bff",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onPatientEvent(String message) {
        Map<String, Object> raw = extract(message, objectMapper);
        if (raw == null) return;

        Map<String, Object> payload = unwrapFull(raw);

        String healthId   = str(payload, "health_id", "healthId");
        String tenantId   = str(payload, "tenant_id", "tenantId");
        String givenName  = str(payload, "given_name", "givenName");
        String familyName = str(payload, "family_name", "familyName");
        String dob        = str(payload, "date_of_birth", "dateOfBirth");
        String sex        = str(payload, "sex");
        String status     = str(payload, "status");
        String crid       = str(payload, "crid");

        if (healthId == null || tenantId == null) {
            log.warn("PatientEventConsumer: missing healthId or tenantId, skipping. payload keys={}", payload.keySet());
            return;
        }

        if (givenName == null) givenName = "Unknown";
        if (familyName == null) familyName = "Unknown";
        if (status == null) status = "PROVISIONAL";

        LocalDate dateOfBirth = null;
        if (dob != null) {
            try {
                dateOfBirth = LocalDate.parse(dob.substring(0, 10));
            } catch (Exception e) {
                log.warn("PatientEventConsumer: could not parse date_of_birth '{}': {}", dob, e.getMessage());
            }
        }

        String cpid = crid != null ? crid : healthId;

        OffsetDateTime now = OffsetDateTime.now();

        jdbc.update("""
            INSERT INTO patients
                (id, tenant_id, cpid, given_name, family_name, date_of_birth, sex, status, created_at, updated_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (tenant_id, cpid) DO UPDATE SET
                given_name   = EXCLUDED.given_name,
                family_name  = EXCLUDED.family_name,
                date_of_birth = COALESCE(EXCLUDED.date_of_birth, patients.date_of_birth),
                sex          = COALESCE(EXCLUDED.sex, patients.sex),
                status       = EXCLUDED.status,
                updated_at   = EXCLUDED.updated_at
            """,
                tenantId, cpid, givenName, familyName, dateOfBirth, sex, status, now, now);

        log.info("PatientEventConsumer: upserted patient cpid={} tenantId={} status={}", cpid, tenantId, status);
    }
}
