package zw.gov.mohcc.impilo.tshepo.authz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.authz.dto.AuthzInternalRequest;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.EventOutboxRepository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Publishes authorization audit events to Kafka via the transactional outbox pattern.
 *
 * <p>The dual-write problem (DB + Kafka) is solved by:
 * <ol>
 *   <li>Writing an outbox entry in the same DB transaction as the decision log</li>
 *   <li>A background scheduler polls for unpublished entries and publishes them to Kafka</li>
 *   <li>On successful publish, the entry is marked as published</li>
 * </ol>
 */
@Service
public class AuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuditPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AuthzProperties properties;
    private final ObjectMapper objectMapper;

    public AuditPublisher(EventOutboxRepository outboxRepository,
                          KafkaTemplate<String, Object> kafkaTemplate,
                          AuthzProperties properties,
                          ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Create an outbox entry for an authorization decision.
     * This must be called within the same transaction as the decision log persist.
     */
    @Transactional
    public void queueAuditEvent(AuthzInternalRequest request, String verdict,
                                 int riskScore, String denyReason) {
        queueAuditEvent(request, verdict, riskScore, denyReason, null);
    }

    @Transactional
    public void queueAuditEvent(AuthzInternalRequest request, String verdict,
                                 int riskScore, String denyReason,
                                 zw.gov.mohcc.impilo.tshepo.contracts.dto.Obligations obligations) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "AUTHZ_DECISION");
        payload.put("tenantId", request.tenantId() != null ? request.tenantId().toString() : null);
        payload.put("correlationId", request.correlationId() != null ? request.correlationId().toString() : null);
        payload.put("actorId", request.actorId());
        payload.put("actorType", request.actorType());
        payload.put("action", request.action());
        payload.put("resourceType", request.resourceType());
        payload.put("resourceId", request.resourceId());
        payload.put("purposeOfUse", request.purposeOfUse());
        payload.put("verdict", verdict);
        payload.put("riskScore", riskScore);
        payload.put("denyReason", denyReason);
        payload.put("facilityId", request.facilityId() != null ? request.facilityId().toString() : null);
        payload.put("workspaceId", request.workspaceId() != null ? request.workspaceId().toString() : null);
        payload.put("deviceFingerprint", request.deviceFingerprint());
        payload.put("timestamp", Instant.now().toString());
        if (obligations != null && obligations.visibilityProfile() != null) {
            payload.put("visibilityTier", obligations.visibilityProfile().visibilityTier());
            payload.put("piiAccess", obligations.visibilityProfile().piiAccess());
            payload.put("clinicalAccess", obligations.visibilityProfile().clinicalAccess());
            payload.put("aggregateOnly", obligations.visibilityProfile().aggregateOnly());
            payload.put("escalationGrantId", obligations.visibilityProfile().escalationGrantId());
        }

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize audit event payload: {}", e.getMessage());
            payloadJson = "{}";
        }

        EventOutboxEntity entry = new EventOutboxEntity();
        entry.setAggregateType("AuthzDecision");
        entry.setAggregateId(request.correlationId() != null ? request.correlationId().toString() : "unknown");
        entry.setEventType("AUTHZ_DECISION");
        entry.setPayload(payloadJson);

        outboxRepository.save(entry);
    }

    /**
     * Background scheduler that publishes unpublished outbox entries to Kafka.
     * Runs every 2 seconds.
     */
    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void publishPendingEvents() {
        List<EventOutboxEntity> pending = outboxRepository.findUnpublished();
        if (pending.isEmpty()) {
            return;
        }

        String topic = properties.getAuditKafkaTopic();
        int published = 0;

        for (EventOutboxEntity entry : pending) {
            try {
                Object payload;
                try {
                    payload = objectMapper.readValue(entry.getPayload(), Map.class);
                } catch (Exception e) {
                    payload = entry.getPayload();
                }

                kafkaTemplate.send(topic, entry.getAggregateId(), payload);

                entry.setPublishedAt(Instant.now());
                outboxRepository.save(entry);
                published++;
            } catch (Exception e) {
                log.error("Failed to publish outbox entry {} to Kafka: {}",
                        entry.getId(), e.getMessage());
                // Stop processing batch on failure — will retry next cycle
                break;
            }
        }

        if (published > 0) {
            log.info("Published {} audit events to Kafka topic '{}'", published, topic);
        }
    }

    /**
     * Governance / visibility escalation events (same Kafka topic as authz audit stream).
     */
    @Transactional
    public void queueGovernanceEvent(String eventType, String aggregateId, Map<String, Object> payload) {
        Map<String, Object> body = new HashMap<>(payload);
        body.put("eventType", eventType);
        body.put("timestamp", Instant.now().toString());

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize governance event: {}", e.getMessage());
            payloadJson = "{}";
        }

        EventOutboxEntity entry = new EventOutboxEntity();
        entry.setAggregateType("Governance");
        entry.setAggregateId(aggregateId != null ? aggregateId : "unknown");
        entry.setEventType(eventType);
        entry.setPayload(payloadJson);
        outboxRepository.save(entry);
    }
}
