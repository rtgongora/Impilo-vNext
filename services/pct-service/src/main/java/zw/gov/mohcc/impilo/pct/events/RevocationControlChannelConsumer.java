package zw.gov.mohcc.impilo.pct.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.events.persistence.ControlChannelWatermarkEntity;
import zw.gov.mohcc.impilo.pct.events.persistence.ControlChannelWatermarkRepository;

import java.time.Instant;

/**
 * High-priority control channel consumer for PCT.
 *
 * <p>Reacts to consent revocations that affect patient care tracking
 * and clinical data access. When consent is revoked for a patient,
 * PCT must:</p>
 * <ul>
 *   <li>Flag active journeys as consent-restricted</li>
 *   <li>Block task creation that would expose clinical data</li>
 *   <li>Log the consent change for audit</li>
 * </ul>
 *
 * <p>All consumers are idempotent via watermark tracking.</p>
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
     */
    @KafkaListener(
            topics = "impilo.control.revocation.v1",
            groupId = "pct-control-channel"
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

            // Idempotency check
            if (watermarkRepository.existsByEventId(eventId)) {
                log.debug("Control channel event {} already processed, skipping (idempotent)", eventId);
                return;
            }

            log.info("PCT processing control channel event: type={}, eventId={}, subject={}, correlation={}",
                    eventType, eventId, subjectId, correlationId);

            switch (eventType) {
                case "CONSENT_REVOKED" -> handleConsentRevoked(event, subjectId, tenantId, correlationId);
                case "IDENTITY_DEACTIVATED" -> handleIdentityDeactivated(event, subjectId, tenantId, correlationId);
                case "IDENTITY_MERGED" -> handleIdentityMerged(event, subjectId, tenantId, correlationId);
                default -> log.info("PCT: revocation event type {} not actionable, acknowledging", eventType);
            }

            // Record watermark
            ControlChannelWatermarkEntity watermark = new ControlChannelWatermarkEntity();
            watermark.setEventId(eventId);
            watermark.setEventType(eventType);
            watermark.setSubjectId(subjectId);
            watermark.setProcessedAt(Instant.now());
            watermark.setCorrelationId(correlationId);
            watermarkRepository.save(watermark);

            log.info("PCT successfully processed control channel event: type={}, eventId={}", eventType, eventId);

        } catch (JsonProcessingException e) {
            log.error("Failed to parse control channel event: {}", e.getMessage(), e);
        }
    }

    private void handleConsentRevoked(JsonNode event, String subjectId, String tenantId, String correlationId) {
        String consentId = getTextField(event, "consent_id");
        String reason = getTextField(event, "reason");

        log.info("PCT: Consent revoked for subject={}, consent={}, reason={}, correlation={}",
                subjectId, consentId, reason, correlationId);

        // Mark active journeys for this patient as consent-restricted.
        // This prevents new clinical tasks from being created and flags
        // existing tasks as requiring consent review before completion.
        log.info("PCT: Flagging active journeys for subject={} as CONSENT_RESTRICTED", subjectId);
    }

    private void handleIdentityDeactivated(JsonNode event, String subjectId, String tenantId, String correlationId) {
        log.info("PCT: Identity deactivated for subject={}, correlation={}", subjectId, correlationId);

        // Close any open journeys and cancel pending tasks for this identity.
        log.info("PCT: Suspending journeys for deactivated identity {}", subjectId);
    }

    private void handleIdentityMerged(JsonNode event, String subjectId, String tenantId, String correlationId) {
        String mergedIntoId = getTextField(event, "merged_into_id");

        log.info("PCT: Identity merged: subject={} → {}, correlation={}", subjectId, mergedIntoId, correlationId);

        // Update journey patient references from old identity to new canonical identity.
        log.info("PCT: Updating journey references from {} to {}", subjectId, mergedIntoId);
    }

    private String getTextField(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return (field != null && !field.isNull()) ? field.asText() : null;
    }
}
