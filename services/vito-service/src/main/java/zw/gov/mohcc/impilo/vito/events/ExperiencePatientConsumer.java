package zw.gov.mohcc.impilo.vito.events;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.vito.core.IdentityService;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Consumes {@code experience.patient} events published by the experience-bff when a
 * provisional patient is registered via the UI.
 *
 * <p>Flow:</p>
 * <ol>
 *   <li>BFF registers patient locally (status=PROVISIONAL, cpid=bff_cpid)</li>
 *   <li>BFF publishes {@code impilo.experience.patient.registered.v1} to {@code experience.patient}</li>
 *   <li>This consumer registers the patient in VITO using the BFF's patient UUID as CRID</li>
 *   <li>VITO publishes {@code IDENTITY_CREATED} via its outbox → {@code vito.identity}</li>
 *   <li>BFF {@code PatientEventConsumer} receives the event and updates the provisional
 *       patient's CPID to the authoritative VITO healthId</li>
 * </ol>
 *
 * <p>Idempotent: replayed messages with the same {@code bff_patient_id} are no-ops.</p>
 */
@Component
public class ExperiencePatientConsumer {

    private static final Logger log = LoggerFactory.getLogger(ExperiencePatientConsumer.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final IdentityService identityService;
    private final ObjectMapper objectMapper;

    public ExperiencePatientConsumer(IdentityService identityService, ObjectMapper objectMapper) {
        this.identityService = identityService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "experience.patient",
            groupId = "vito-experience-channel",
            containerFactory = "experienceChannelListenerFactory"
    )
    public void onExperiencePatient(String message) {
        Map<String, Object> payload = extractPayload(message);
        if (payload == null) return;

        String bffPatientIdStr = str(payload, "bff_patient_id");
        String tenantLabel     = str(payload, "tenant_id");
        String givenName       = str(payload, "given_name");
        String familyName      = str(payload, "family_name");
        String dateOfBirth     = str(payload, "date_of_birth");
        String sex             = str(payload, "sex");

        if (bffPatientIdStr == null || tenantLabel == null) {
            log.warn("ExperiencePatientConsumer: missing bff_patient_id or tenant_id, keys={}", payload.keySet());
            return;
        }

        UUID bffPatientId;
        try {
            bffPatientId = UUID.fromString(bffPatientIdStr);
        } catch (IllegalArgumentException e) {
            log.error("ExperiencePatientConsumer: invalid bff_patient_id '{}': {}", bffPatientIdStr, e.getMessage());
            return;
        }

        UUID tenantUuid = UUID.nameUUIDFromBytes(tenantLabel.getBytes(StandardCharsets.UTF_8));

        log.info("ExperiencePatientConsumer: registering patient bff_patient_id={} tenant={}", bffPatientId, tenantLabel);

        identityService.registerFromExperience(
                bffPatientId, tenantUuid, tenantLabel,
                givenName, familyName, dateOfBirth, sex);
    }

    private Map<String, Object> extractPayload(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            boolean isEnvelope = root.has("event_id") || root.has("eventId");
            if (isEnvelope) {
                JsonNode payloadNode = root.path("payload");
                if (payloadNode.isMissingNode() || payloadNode.isNull()) {
                    log.warn("ExperiencePatientConsumer: envelope has no payload, skipping");
                    return null;
                }
                Map<String, Object> payload = objectMapper.convertValue(payloadNode, MAP_TYPE);
                String tenantId = root.path("tenant_id").asText(null);
                if (tenantId != null && payload.get("tenant_id") == null) {
                    payload.put("tenant_id", tenantId);
                }
                return payload;
            }
            return objectMapper.convertValue(root, MAP_TYPE);
        } catch (Exception e) {
            log.error("ExperiencePatientConsumer: failed to parse message: {}", e.getMessage(), e);
            return null;
        }
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }
}
