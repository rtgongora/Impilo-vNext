package zw.gov.mohcc.impilo.experience.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Consumes BUTANO FHIR resource events and syncs clinical data into BFF-local tables.
 *
 * <p>Topics consumed:</p>
 * <ul>
 *   <li>{@code butano.resource.created} — new FHIR resource stored in the SHR</li>
 *   <li>{@code butano.resource.updated} — existing FHIR resource modified</li>
 * </ul>
 *
 * <p>Resource types handled:</p>
 * <ul>
 *   <li>{@code Encounter}   → upserts into {@code encounters} table</li>
 *   <li>{@code Observation} → upserts into {@code vitals_records} table (linked by encounter)</li>
 * </ul>
 *
 * <p>FHIR JSON parsed directly without HAPI library. Standard R4 structure assumed.</p>
 */
@Component
public class EncounterEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EncounterEventConsumer.class);

    private static final String LOINC_SYSTOLIC       = "8480-6";
    private static final String LOINC_DIASTOLIC      = "8462-4";
    private static final String LOINC_HEART_RATE     = "8867-4";
    private static final String LOINC_TEMPERATURE    = "8310-5";
    private static final String LOINC_RESP_RATE      = "9279-1";
    private static final String LOINC_O2_SAT         = "59408-5";
    private static final String LOINC_WEIGHT         = "29463-7";
    private static final String LOINC_HEIGHT         = "8302-2";
    private static final String LOINC_BMI            = "39156-5";
    private static final String LOINC_PAIN_SCORE     = "72514-3";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public EncounterEventConsumer(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {"butano.resource.created", "butano.resource.updated"},
            groupId = "experience-bff",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onFhirResourceEvent(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String resourceType = root.path("resourceType").asText(null);
            if (resourceType == null) {
                log.debug("EncounterEventConsumer: no resourceType in message, skipping");
                return;
            }
            switch (resourceType) {
                case "Encounter"    -> handleEncounter(root);
                case "Observation"  -> handleObservation(root);
                default             -> log.debug("EncounterEventConsumer: ignoring resourceType={}", resourceType);
            }
        } catch (Exception e) {
            log.error("EncounterEventConsumer: failed to process message: {}", e.getMessage(), e);
        }
    }

    // ── Encounter ────────────────────────────────────────────────────────

    private void handleEncounter(JsonNode enc) {
        String fhirId        = enc.path("id").asText(null);
        String status        = enc.path("status").asText("unknown");
        String tenantId      = extractTenantId(enc);
        String cpid          = extractSubjectRef(enc);
        String encounterClass = enc.path("class").path("code").asText(null);
        String chiefComplaint = enc.path("reasonCode").path(0).path("text").asText(null);
        String startedAt     = enc.path("period").path("start").asText(null);
        String endedAt       = enc.path("period").path("end").asText(null);

        if (fhirId == null || tenantId == null) {
            log.warn("EncounterEventConsumer: Encounter missing id or tenantId, skipping");
            return;
        }

        UUID patientUuid = resolvePatientByCpid(tenantId, cpid);
        if (patientUuid == null) {
            log.warn("EncounterEventConsumer: patient not found cpid={} tenantId={}, skipping Encounter", cpid, tenantId);
            return;
        }

        UUID facilityUuid = resolveFacilityFromEncounter(enc, tenantId);

        OffsetDateTime now   = OffsetDateTime.now();
        OffsetDateTime start = parseDateTime(startedAt, now);
        OffsetDateTime end   = parseDateTime(endedAt, null);

        jdbc.update("""
            INSERT INTO encounters
                (id, tenant_id, facility_id, patient_id, encounter_type, status,
                 chief_complaint, started_at, ended_at, created_at, updated_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """,
                tenantId, facilityUuid, patientUuid,
                encounterClass, status, chiefComplaint,
                start, end, now, now);

        log.info("EncounterEventConsumer: processed Encounter fhirId={} cpid={} status={}", fhirId, cpid, status);
    }

    // ── Observation (vitals) ─────────────────────────────────────────────

    private void handleObservation(JsonNode obs) {
        String fhirId   = obs.path("id").asText(null);
        String tenantId = extractTenantId(obs);
        String cpid     = extractSubjectRef(obs);

        if (fhirId == null || tenantId == null) {
            log.warn("EncounterEventConsumer: Observation missing id or tenantId, skipping");
            return;
        }

        UUID patientUuid = resolvePatientByCpid(tenantId, cpid);
        if (patientUuid == null) {
            log.debug("EncounterEventConsumer: patient not found cpid={} for Observation, skipping", cpid);
            return;
        }

        String encounterRef = obs.path("encounter").path("reference").asText(null);
        UUID encounterId = resolveEncounterByFhirRef(tenantId, encounterRef);
        if (encounterId == null) {
            log.debug("EncounterEventConsumer: encounter not found ref={} for Observation, skipping", encounterRef);
            return;
        }

        String loincCode  = obs.path("code").path("coding").path(0).path("code").asText(null);
        Double value      = extractQuantityValue(obs);
        String recordedAt = obs.path("effectiveDateTime").asText(null);
        OffsetDateTime recorded = parseDateTime(recordedAt, OffsetDateTime.now());

        if (loincCode == null || value == null) {
            log.debug("EncounterEventConsumer: Observation missing loinc or value, skipping fhirId={}", fhirId);
            return;
        }

        String col = loincToColumn(loincCode);
        if (col == null) {
            log.debug("EncounterEventConsumer: unknown LOINC {} for vitals, skipping", loincCode);
            return;
        }

        String recordedByDefault = "system";
        OffsetDateTime now = OffsetDateTime.now();

        boolean isDecimal = col.equals("bmi") || col.equals("temperature")
                || col.equals("weight") || col.equals("height") || col.equals("oxygen_saturation");
        Object colValue = isDecimal ? value : value.intValue();

        int updated = jdbc.update(
                "UPDATE vitals_records SET " + col + " = ? WHERE encounter_id = ? AND tenant_id = ?",
                colValue, encounterId, tenantId);

        if (updated == 0) {
            jdbc.update("""
                INSERT INTO vitals_records
                    (id, tenant_id, patient_id, encounter_id, recorded_by, recorded_at, created_at)
                VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """,
                    tenantId, patientUuid, encounterId, recordedByDefault, recorded, now);

            jdbc.update(
                    "UPDATE vitals_records SET " + col + " = ? WHERE encounter_id = ? AND tenant_id = ?",
                    colValue, encounterId, tenantId);
        }

        log.debug("EncounterEventConsumer: set vitals col={} value={} encounterId={}", col, value, encounterId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String loincToColumn(String loinc) {
        return switch (loinc) {
            case LOINC_SYSTOLIC    -> "systolic";
            case LOINC_DIASTOLIC   -> "diastolic";
            case LOINC_HEART_RATE  -> "heart_rate";
            case LOINC_TEMPERATURE -> "temperature";
            case LOINC_RESP_RATE   -> "respiratory_rate";
            case LOINC_O2_SAT      -> "oxygen_saturation";
            case LOINC_WEIGHT      -> "weight";
            case LOINC_HEIGHT      -> "height";
            case LOINC_BMI         -> "bmi";
            case LOINC_PAIN_SCORE  -> "pain_score";
            default                -> null;
        };
    }

    private String extractTenantId(JsonNode resource) {
        JsonNode tags = resource.path("meta").path("tag");
        for (JsonNode tag : tags) {
            String system = tag.path("system").asText("");
            if (system.contains("tenant") || system.contains("impilo")) {
                String code = tag.path("code").asText(null);
                if (code != null) return code;
            }
        }
        return null;
    }

    private String extractSubjectRef(JsonNode resource) {
        String ref = resource.path("subject").path("reference").asText(null);
        if (ref != null && ref.contains("/")) return ref.substring(ref.lastIndexOf('/') + 1);
        return ref;
    }

    private UUID resolvePatientByCpid(String tenantId, String cpid) {
        if (cpid == null) return null;
        try {
            return jdbc.queryForObject(
                    "SELECT id FROM patients WHERE tenant_id = ? AND cpid = ?",
                    UUID.class, tenantId, cpid);
        } catch (Exception e) {
            return null;
        }
    }

    private UUID resolveFacilityFromEncounter(JsonNode enc, String tenantId) {
        String ref = enc.path("location").path(0).path("location").path("reference").asText(null);
        if (ref != null && ref.contains("/")) {
            try { return UUID.fromString(ref.substring(ref.lastIndexOf('/') + 1)); }
            catch (IllegalArgumentException ignored) {}
        }
        try {
            return jdbc.queryForObject(
                    "SELECT id FROM facilities WHERE tenant_id = ? ORDER BY created_at LIMIT 1",
                    UUID.class, tenantId);
        } catch (Exception ignored) { return null; }
    }

    private UUID resolveEncounterByFhirRef(String tenantId, String fhirRef) {
        if (fhirRef == null) return null;
        try {
            return jdbc.queryForObject(
                    "SELECT id FROM encounters WHERE tenant_id = ? ORDER BY started_at DESC LIMIT 1",
                    UUID.class, tenantId);
        } catch (Exception e) {
            return null;
        }
    }

    private Double extractQuantityValue(JsonNode obs) {
        JsonNode vq = obs.path("valueQuantity");
        if (!vq.isMissingNode() && vq.has("value")) {
            try { return vq.path("value").asDouble(); } catch (Exception ignored) {}
        }
        return null;
    }

    private OffsetDateTime parseDateTime(String value, OffsetDateTime fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return OffsetDateTime.parse(value); }
        catch (Exception e) { return fallback; }
    }
}
