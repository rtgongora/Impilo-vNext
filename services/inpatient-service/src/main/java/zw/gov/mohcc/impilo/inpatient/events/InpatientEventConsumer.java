package zw.gov.mohcc.impilo.inpatient.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.inpatient.core.AdmissionService;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.AdmissionEntity;

import java.util.List;
import java.util.UUID;

/**
 * Consumes PCT patient journey events to keep inpatient census aligned
 * (auto-discharge when the facility journey ends or death is recorded).
 *
 * <p>PCT currently publishes journey state changes to {@code pct.journey.state_changed}
 * with a raw JSON payload. This consumer also listens on {@code pct.journey} and
 * {@code impilo.pct.journey} for forward-compatible routing.</p>
 */
@Component
@Profile("!test")
public class InpatientEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(InpatientEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final AdmissionService admissionService;

    public InpatientEventConsumer(ObjectMapper objectMapper, AdmissionService admissionService) {
        this.objectMapper = objectMapper;
        this.admissionService = admissionService;
    }

    /**
     * Consume PCT admission events. The PCT&lt;-&gt;inpatient handshake: on {@code ADMISSION_APPROVED}, PCT has
     * approved the admission DECISION; inpatient-service materialises the physical CENSUS admission (bed/ward)
     * and echoes a bed-assignment back (which PCT stamps via {@code linkInpatientAdmission}). PCT owns the
     * decision; inpatient owns the census — no field is duplicated as a second source-of-truth.
     */
    @KafkaListener(
            topics = {"pct.admission.updated"},
            groupId = "inpatient-service"
    )
    public void consumePctAdmission(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventType = firstNonBlank(text(root, "event_type"), text(root, "eventType"));
            if (!"ADMISSION_APPROVED".equals(eventType)) {
                // ADMISSION_REQUESTED / PATIENT_ADMITTED are PCT-internal lifecycle markers; only the approval
                // hands a census admission to inpatient. Ignore the rest (idempotent, no-op).
                return;
            }

            JsonNode payload = extractPayload(root);
            if (payload == null || payload.isNull() || isConsentDenied(payload)) {
                return;
            }

            String pctAdmissionId = text(payload, "admissionId");
            String tenantId = firstNonBlank(text(root, "tenant_id"), text(payload, "tenantId"), text(payload, "tenant_id"));
            String subjectCpid = firstNonBlank(text(payload, "subjectCpid"), text(payload, "patientCpid"),
                    text(payload, "patient_cpid"));
            String facilityId = firstNonBlank(text(payload, "facilityId"), text(payload, "facility_id"));
            if (pctAdmissionId == null || tenantId == null || subjectCpid == null || facilityId == null) {
                log.warn("INPATIENT: ADMISSION_APPROVED missing required fields "
                        + "(admissionId/tenant/subject/facility), skipping");
                return;
            }

            UUID encounterId = parseUuid(text(payload, "encounterId"));
            UUID wardId = parseUuid(text(payload, "wardId"));
            UUID bedId = parseUuid(text(payload, "bedId"));
            String admittingDiagnosis = text(payload, "admittingDiagnosis");
            String admissionType = text(payload, "admissionType");

            admissionService.admitFromPctApproval(
                    UUID.fromString(pctAdmissionId.trim()),
                    UUID.fromString(tenantId.trim()),
                    UUID.fromString(facilityId.trim()),
                    subjectCpid,
                    encounterId,
                    wardId,
                    bedId,
                    admittingDiagnosis,
                    admissionType);
            log.info("INPATIENT: materialised census admission for PCT admission {} (subject={})",
                    pctAdmissionId, subjectCpid);
        } catch (JsonProcessingException e) {
            log.error("INPATIENT: failed to parse PCT admission event: {}", e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("INPATIENT: error handling PCT admission event: {}", e.getMessage(), e);
        }
    }

    private static UUID parseUuid(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(v.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @KafkaListener(
            topics = {"pct.journey", "impilo.pct.journey", "pct.journey.state_changed"},
            groupId = "inpatient-service"
    )
    public void consumePctJourney(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String correlationId = firstNonBlank(text(root, "correlation_id"), text(root, "correlationId"));
            String eventType = firstNonBlank(text(root, "event_type"), text(root, "eventType"), "pct.journey");

            String tenantId = root.path("tenant_id").asText(null);
            if (tenantId == null || tenantId.isBlank()) {
                log.warn("Event missing tenant_id — skipping: correlation={}", correlationId);
                return;
            }
            log.info("Processing {} event for tenant={} correlation={}", eventType, tenantId, correlationId);

            JsonNode payload = extractPayload(root);
            if (payload == null || payload.isNull()) {
                log.warn("INPATIENT: PCT journey event missing payload, skipping");
                return;
            }

            if (isConsentDenied(payload)) {
                log.debug("INPATIENT: skipping PCT journey handling due to consent flags");
                return;
            }

            String newState = text(payload, "newState");
            if (newState == null) {
                newState = text(payload, "state");
            }
            if (newState == null) {
                log.debug("INPATIENT: journey event without state field, ignoring");
                return;
            }

            if (!triggersAutoDischarge(newState)) {
                return;
            }

            String patientCpid = firstNonBlank(
                    text(payload, "patientCpid"),
                    text(payload, "patient_cpid"));
            String facilityIdStr = firstNonBlank(
                    text(payload, "facilityId"),
                    text(payload, "facility_id"));

            if (patientCpid == null || facilityIdStr == null) {
                log.warn("INPATIENT: cannot auto-discharge — missing patientCpid or facilityId in payload");
                return;
            }

            UUID facilityId = UUID.fromString(facilityIdStr.trim());
            List<AdmissionEntity> active = admissionService.findActiveAdmissionsForPatientAtFacility(
                    patientCpid, facilityId);
            active = active.stream()
                    .filter(a -> tenantId.equals(a.getTenantId().toString()))
                    .toList();
            if (active.isEmpty()) {
                log.warn("INPATIENT: no active admission for tenant {} — skipping auto-discharge "
                                + "(patientCpid={}, facilityId={})",
                        tenantId, patientCpid, facilityId);
                return;
            }

            for (AdmissionEntity admission : active) {
                try {
                    admissionService.dischargePatient(admission.getAdmissionRef());
                    log.info("INPATIENT: auto-discharged admission {} for patientCpid={} on PCT journey state {}",
                            admission.getAdmissionRef(), patientCpid, newState);
                } catch (RuntimeException e) {
                    log.error("INPATIENT: auto-discharge failed for admission {}: {}",
                            admission.getAdmissionRef(), e.getMessage());
                }
            }
        } catch (JsonProcessingException e) {
            log.error("INPATIENT: failed to parse PCT journey event: {}", e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("INPATIENT: error handling PCT journey event: {}", e.getMessage(), e);
        }
    }

    private static boolean triggersAutoDischarge(String newState) {
        if (newState == null) {
            return false;
        }
        return switch (newState) {
            case "DISCHARGED", "DEATH_RECORDED", "CANCELLED",
                 "LEFT_WITHOUT_BEING_SEEN", "NO_SHOW" -> true;
            default -> false;
        };
    }

    private JsonNode extractPayload(JsonNode root) {
        if (!root.has("payload")) {
            return root;
        }
        JsonNode p = root.get("payload");
        if (p == null || p.isNull()) {
            return null;
        }
        if (p.isObject()) {
            return p;
        }
        if (p.isTextual()) {
            try {
                return objectMapper.readTree(p.asText());
            } catch (JsonProcessingException e) {
                log.warn("INPATIENT: failed to parse textual envelope payload: {}", e.getMessage());
                return null;
            }
        }
        return p;
    }

    private static boolean isConsentDenied(JsonNode payload) {
        String consentStatus = text(payload, "consentStatus");
        if (consentStatus != null && (consentStatus.equalsIgnoreCase("REVOKED")
                || consentStatus.equalsIgnoreCase("DENIED"))) {
            return true;
        }
        if (payload.has("consentGranted") && payload.get("consentGranted").isBoolean()) {
            return !payload.get("consentGranted").asBoolean();
        }
        return false;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText(null);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
