package zw.gov.mohcc.impilo.pct.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

import zw.gov.mohcc.impilo.pct.core.EmergencyEpisodeService;
import zw.gov.mohcc.impilo.pct.integration.InpatientActivationLinkClient;
import zw.gov.mohcc.impilo.pct.persistence.entity.AdmissionEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.AdmissionRepository;

/**
 * Consumes inpatient-service's {@code EMERGENCY_ACTIVATION_RAISED} (W6b) — a code called on an
 * already-admitted patient (ward arrest, deteriorating post-op patient, etc.), as opposed to a
 * fresh arrival through the ED door. The plan's own words: "in-facility deterioration minting on the
 * admission's existing journey via the emergency_episode_id correlation column added to
 * emergency_activation in W6a." This is that minting.
 *
 * <p>Anchors on the admission's EXISTING journey — never mints a second one. Resolves it via the
 * admission handshake (V018, {@code pct_admissions.inpatient_admission_ref}), the same back-link
 * {@link InpatientBedAssignedConsumer} completes on the other side of the same handshake. An
 * activation with no {@code admissionRef}, or one that resolves to no known PCT admission, is skipped
 * — not silently anchored to nothing, and not guessed at. {@code IN_FACILITY_DETERIORATION} is a
 * pre-declared entry route (V200's own {@code chk_ee_entry_route}) precisely for this case.
 *
 * <p>Idempotent mint: {@link EmergencyEpisodeService#open} keys replay on
 * {@code (entryRoute, entrySourceRef)} — here {@code entrySourceRef = activationId}, so a redelivered
 * event (or a genuinely repeated Kafka delivery) returns the same episode rather than minting twice.
 */
@Component
@Profile("!test")
public class InFacilityDeteriorationConsumer {

    private static final Logger log = LoggerFactory.getLogger(InFacilityDeteriorationConsumer.class);
    private static final String SYSTEM_ACTOR = "system:inpatient-activation-consumer";

    private final ObjectMapper objectMapper;
    private final AdmissionRepository admissionRepository;
    private final EmergencyEpisodeService episodeService;
    private final InpatientActivationLinkClient linkClient;

    public InFacilityDeteriorationConsumer(ObjectMapper objectMapper,
                                           AdmissionRepository admissionRepository,
                                           EmergencyEpisodeService episodeService,
                                           InpatientActivationLinkClient linkClient) {
        this.objectMapper = objectMapper;
        this.admissionRepository = admissionRepository;
        this.episodeService = episodeService;
        this.linkClient = linkClient;
    }

    @KafkaListener(topics = {"inpatient.emergency"}, groupId = "pct-service-in-facility-deterioration")
    public void onInpatientEmergency(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventType = firstNonBlank(text(root, "event_type"), text(root, "eventType"));
            if (!"EMERGENCY_ACTIVATION_RAISED".equals(eventType)) {
                return; // ENDED / actions / etc. are not this consumer's concern
            }

            JsonNode payload = extractPayload(root);
            if (payload == null || payload.isNull()) {
                return;
            }

            UUID tenantId = parseUuid(text(payload, "tenantId"));
            UUID activationId = parseUuid(text(payload, "activationId"));
            UUID admissionRef = parseUuid(text(payload, "admissionRef"));
            if (tenantId == null || activationId == null) {
                log.warn("PCT: EMERGENCY_ACTIVATION_RAISED missing tenantId or activationId, skipping");
                return;
            }
            if (admissionRef == null) {
                // Not every activation follows an admission (an ED-called code has its own entry
                // route already). Nothing to anchor onto here — not an error, just out of scope.
                return;
            }

            AdmissionEntity admission = admissionRepository
                    .findByTenantIdAndInpatientAdmissionRef(tenantId, admissionRef)
                    .orElse(null);
            if (admission == null) {
                log.warn("PCT: EMERGENCY_ACTIVATION_RAISED for inpatient admission {} has no PCT "
                        + "admission handshake yet — cannot anchor an episode without a real journey, "
                        + "skipping this round", admissionRef);
                return;
            }

            String subjectCpid = firstNonBlank(admission.getPatientCpid(), text(payload, "subjectCpid"));
            String activationTimeStr = text(payload, "activationTime");
            OffsetDateTime arrivedAt = activationTimeStr != null
                    ? parseOffsetDateTime(activationTimeStr) : OffsetDateTime.now();

            var cmd = new EmergencyEpisodeService.OpenEpisodeCommand(
                    tenantId,
                    admission.getFacilityId(),
                    "IN_FACILITY_DETERIORATION",
                    "inpatient-service",
                    activationId.toString(),
                    message,
                    "UNDIFFERENTIATED",
                    subjectCpid,
                    admission.getJourneyId(),
                    null,
                    null,
                    null,
                    false,
                    arrivedAt,
                    SYSTEM_ACTOR);
            var result = episodeService.open(cmd);

            linkClient.linkEpisode(tenantId, activationId, result.episode().getEpisodeId());
        } catch (JsonProcessingException e) {
            log.error("PCT: failed to parse inpatient EMERGENCY_ACTIVATION_RAISED event: {}", e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("PCT: error handling inpatient EMERGENCY_ACTIVATION_RAISED event: {}", e.getMessage(), e);
        }
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
                return null;
            }
        }
        return p;
    }

    private static UUID parseUuid(String v) {
        if (v == null || v.isBlank()) return null;
        try { return UUID.fromString(v.trim()); } catch (IllegalArgumentException e) { return null; }
    }

    private static OffsetDateTime parseOffsetDateTime(String v) {
        try { return OffsetDateTime.parse(v.trim()); } catch (RuntimeException e) { return OffsetDateTime.now(); }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText(null);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
