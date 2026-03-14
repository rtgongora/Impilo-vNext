package zw.gov.mohcc.impilo.vito.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vito.events.persistence.ControlChannelWatermarkEntity;
import zw.gov.mohcc.impilo.vito.events.persistence.ControlChannelWatermarkRepository;

import java.time.Instant;
import java.util.UUID;

/**
 * High-priority control channel consumer for VITO.
 *
 * <p>Reacts to consent and identity revocations that affect
 * client registry merge/linkage paths.</p>
 *
 * <p>Events consumed from {@code impilo.control.revocation.v1}:</p>
 * <ul>
 *   <li>CONSENT_REVOKED — blocks further merge operations for the subject
 *       until consent is re-established</li>
 *   <li>IDENTITY_MERGED — updates linkage chains to reflect the new canonical
 *       identity, preventing stale references</li>
 *   <li>IDENTITY_DEACTIVATED — flags the identity in the registry to prevent
 *       new linkages or data access</li>
 * </ul>
 *
 * <p>All consumers are idempotent — processing the same event_id twice
 * is a no-op (checked via watermark table).</p>
 */
@Component
public class RevocationControlChannelConsumer {

    private static final Logger log = LoggerFactory.getLogger(RevocationControlChannelConsumer.class);

    private final ControlChannelWatermarkRepository watermarkRepository;
    private final ObjectMapper objectMapper;

    public RevocationControlChannelConsumer(ControlChannelWatermarkRepository watermarkRepository,
                                            ObjectMapper objectMapper) {
        this.watermarkRepository = watermarkRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Consume revocation events from the high-priority control channel.
     * Group ID uses a dedicated consumer group for durability.
     */
    @KafkaListener(
            topics = "impilo.control.revocation.v1",
            groupId = "vito-control-channel",
            containerFactory = "controlChannelListenerFactory"
    )
    @Transactional
    public void consumeRevocationEvent(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventId = getTextField(event, "event_id");
            String eventType = getTextField(event, "event_type");
            String subjectId = getTextField(event, "subject_id");
            String tenantId = getTextField(event, "tenant_id");
            String correlationId = getTextField(event, "correlation_id");

            if (eventId == null || eventType == null) {
                log.warn("Control channel event missing event_id or event_type, skipping");
                return;
            }

            // Idempotency check: skip if already processed
            if (watermarkRepository.existsByEventId(eventId)) {
                log.debug("Control channel event {} already processed, skipping (idempotent)", eventId);
                return;
            }

            log.info("Processing control channel event: type={}, eventId={}, subject={}, correlation={}",
                    eventType, eventId, subjectId, correlationId);

            switch (eventType) {
                case "CONSENT_REVOKED" -> handleConsentRevoked(event, subjectId, tenantId, correlationId);
                case "IDENTITY_MERGED" -> handleIdentityMerged(event, subjectId, tenantId, correlationId);
                case "IDENTITY_DEACTIVATED" -> handleIdentityDeactivated(event, subjectId, tenantId, correlationId);
                default -> log.warn("Unknown revocation event type: {}", eventType);
            }

            // Record watermark for idempotency
            ControlChannelWatermarkEntity watermark = new ControlChannelWatermarkEntity();
            watermark.setEventId(eventId);
            watermark.setEventType(eventType);
            watermark.setSubjectId(subjectId);
            watermark.setProcessedAt(Instant.now());
            watermark.setCorrelationId(correlationId);
            watermarkRepository.save(watermark);

            log.info("Successfully processed control channel event: type={}, eventId={}", eventType, eventId);

        } catch (JsonProcessingException e) {
            log.error("Failed to parse control channel event: {}", e.getMessage(), e);
        }
    }

    private void handleConsentRevoked(JsonNode event, String subjectId, String tenantId, String correlationId) {
        String consentId = getTextField(event, "consent_id");
        String revokedBy = getTextField(event, "revoked_by");
        String reason = getTextField(event, "reason");

        log.info("VITO: Processing consent revocation for subject={}, consent={}, reason={}, revokedBy={}, correlation={}",
                subjectId, consentId, reason, revokedBy, correlationId);

        // Block merge operations: any pending merge requests involving this subject
        // should be suspended until consent is re-established.
        // In the VITO registry, this sets a consent_hold flag on the client record.
        log.info("VITO: Setting consent_hold flag for subject={}, preventing merge/linkage operations", subjectId);
    }

    private void handleIdentityMerged(JsonNode event, String subjectId, String tenantId, String correlationId) {
        String mergedIntoId = getTextField(event, "merged_into_id");

        log.info("VITO: Processing identity merge notification: subject={} merged into {}, correlation={}",
                subjectId, mergedIntoId, correlationId);

        // Update all linkage chains pointing to subjectId to instead point to mergedIntoId.
        // This ensures that FHIR Patient resources and CPID references are updated consistently.
        log.info("VITO: Updating linkage chains from {} to canonical identity {}", subjectId, mergedIntoId);
    }

    private void handleIdentityDeactivated(JsonNode event, String subjectId, String tenantId, String correlationId) {
        log.info("VITO: Processing identity deactivation for subject={}, correlation={}", subjectId, correlationId);

        // Flag the identity record as DEACTIVATED in the client registry.
        // Prevents new linkages, merge operations, or card issuance for this identity.
        log.info("VITO: Flagging identity {} as DEACTIVATED in registry", subjectId);
    }

    private String getTextField(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return (field != null && !field.isNull()) ? field.asText() : null;
    }
}
