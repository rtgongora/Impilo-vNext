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

    @KafkaListener(
            topics = {"pct.journey", "impilo.pct.journey", "pct.journey.state_changed"},
            groupId = "inpatient-service"
    )
    public void consumePctJourney(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode payload = extractPayload(root);
            if (payload == null || payload.isNull()) {
                log.warn("INPATIENT: PCT journey event missing payload, skipping");
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

    private static JsonNode extractPayload(JsonNode root) {
        if (root.has("payload") && root.get("payload").isObject()) {
            return root.get("payload");
        }
        return root;
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
