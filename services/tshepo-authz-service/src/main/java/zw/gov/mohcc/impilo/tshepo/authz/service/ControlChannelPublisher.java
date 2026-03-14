package zw.gov.mohcc.impilo.tshepo.authz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes high-priority control channel events via Kafka.
 *
 * <p>Control channel topics are dedicated, high-priority topics for events
 * that must be processed urgently by consuming services. Unlike the standard
 * outbox pattern (which batches events), control channel events are published
 * synchronously with the originating transaction for lowest latency.</p>
 *
 * <p>Standard control channel topics:</p>
 * <ul>
 *   <li>{@code impilo.control.revocation.v1} — consent and identity revocations</li>
 *   <li>{@code impilo.control.privilege_revocation.v1} — privilege/access revocations</li>
 * </ul>
 *
 * <p>All consumers of these topics must be idempotent and durable.</p>
 */
@Service
public class ControlChannelPublisher {

    private static final Logger log = LoggerFactory.getLogger(ControlChannelPublisher.class);

    public static final String TOPIC_REVOCATION = "impilo.control.revocation.v1";
    public static final String TOPIC_PRIVILEGE_REVOCATION = "impilo.control.privilege_revocation.v1";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public ControlChannelPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                    ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publish a consent revocation event to the high-priority control channel.
     *
     * @param tenantId      the tenant context
     * @param subjectId     the patient/subject whose consent is revoked
     * @param consentId     the consent directive ID
     * @param revokedBy     the actor who triggered revocation
     * @param reason        reason code (e.g. PATIENT_WITHDRAWAL, ADMINISTRATIVE)
     * @param correlationId the correlation ID for tracing
     */
    public void publishConsentRevocation(UUID tenantId, String subjectId, String consentId,
                                          String revokedBy, String reason, String correlationId) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event_type", "CONSENT_REVOKED");
        event.put("event_id", UUID.randomUUID().toString());
        event.put("tenant_id", tenantId.toString());
        event.put("subject_id", subjectId);
        event.put("consent_id", consentId);
        event.put("revoked_by", revokedBy);
        event.put("reason", reason);
        event.put("correlation_id", correlationId);
        event.put("timestamp", Instant.now().toString());
        event.put("priority", "HIGH");

        publish(TOPIC_REVOCATION, subjectId, event, "consent revocation");
    }

    /**
     * Publish an identity revocation event (e.g. merged identity, deactivated patient).
     *
     * @param tenantId       the tenant context
     * @param subjectId      the patient/identity being revoked
     * @param revocationType type: IDENTITY_DEACTIVATED, IDENTITY_MERGED, IDENTITY_FLAGGED
     * @param mergedIntoId   if merged, the target identity (nullable)
     * @param revokedBy      the actor who triggered the revocation
     * @param correlationId  the correlation ID
     */
    public void publishIdentityRevocation(UUID tenantId, String subjectId, String revocationType,
                                           String mergedIntoId, String revokedBy,
                                           String correlationId) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event_type", revocationType);
        event.put("event_id", UUID.randomUUID().toString());
        event.put("tenant_id", tenantId.toString());
        event.put("subject_id", subjectId);
        event.put("revocation_type", revocationType);
        if (mergedIntoId != null) {
            event.put("merged_into_id", mergedIntoId);
        }
        event.put("revoked_by", revokedBy);
        event.put("correlation_id", correlationId);
        event.put("timestamp", Instant.now().toString());
        event.put("priority", "HIGH");

        publish(TOPIC_REVOCATION, subjectId, event, "identity revocation");
    }

    /**
     * Publish a privilege/access revocation event.
     *
     * @param tenantId       the tenant context
     * @param actorId        the actor whose privilege is revoked
     * @param privilegeType  type of privilege being revoked (e.g. ROLE, FACILITY_ACCESS, API_KEY)
     * @param scopeRef       the scope reference (role name, facility ID, etc.)
     * @param revokedBy      who performed the revocation
     * @param correlationId  the correlation ID
     */
    public void publishPrivilegeRevocation(UUID tenantId, String actorId, String privilegeType,
                                            String scopeRef, String revokedBy,
                                            String correlationId) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event_type", "PRIVILEGE_REVOKED");
        event.put("event_id", UUID.randomUUID().toString());
        event.put("tenant_id", tenantId.toString());
        event.put("actor_id", actorId);
        event.put("privilege_type", privilegeType);
        event.put("scope_ref", scopeRef);
        event.put("revoked_by", revokedBy);
        event.put("correlation_id", correlationId);
        event.put("timestamp", Instant.now().toString());
        event.put("priority", "HIGH");

        publish(TOPIC_PRIVILEGE_REVOCATION, actorId, event, "privilege revocation");
    }

    private void publish(String topic, String key, Map<String, Object> event, String description) {
        try {
            kafkaTemplate.send(topic, key, event).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish {} to {}: {}", description, topic, ex.getMessage(), ex);
                } else {
                    log.info("Published {} to {} [partition={}, offset={}]",
                            description, topic,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                }
            });
        } catch (Exception e) {
            log.error("Failed to send {} to Kafka topic {}: {}", description, topic, e.getMessage(), e);
            throw new RuntimeException("Control channel publish failed for " + description, e);
        }
    }
}
