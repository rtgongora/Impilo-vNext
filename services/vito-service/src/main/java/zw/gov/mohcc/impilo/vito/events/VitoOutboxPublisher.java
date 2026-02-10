package zw.gov.mohcc.impilo.vito.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vito.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * VITO Event Outbox Publisher — reliable Kafka delivery with v1.1 DualEmitPolicy.
 *
 * Polls the event_outbox table for unpublished events and publishes
 * them to Kafka. Uses the outbox pattern to ensure at-least-once
 * delivery with transactional consistency.
 *
 * DualEmitPolicy (controlled by vito.v11.emit-mode):
 *   LEGACY_ONLY  — keep current behavior: raw payload to vito.* topics
 *   V1_1_ONLY    — emit only v1.1 EventEnvelope to impilo.vito.* topics
 *   DUAL         — emit both legacy and v1.1
 *
 * Legacy topics:
 *   vito.identity, vito.cards, vito.wallet, vito.issuance, vito.dedup,
 *   vito.offline, vito.alias, vito.pickup, vito.print
 *
 * V1.1 topics:
 *   impilo.vito.identity, impilo.vito.cards, etc.
 *   With EventEnvelope wrapping: event_type = "impilo.vito.<aggregate>.<event>.v1"
 */
@Component
public class VitoOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(VitoOutboxPublisher.class);
    private static final String PRODUCER = "vito";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final EmitMode emitMode;
    private final String defaultPodId;

    public VitoOutboxPublisher(EventOutboxRepository outboxRepository,
                                KafkaTemplate<String, String> kafkaTemplate,
                                @Value("${vito.v11.emit-mode:DUAL}") String emitModeStr,
                                @Value("${vito.v11.default-pod-id:unknown}") String defaultPodId) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.emitMode = parseEmitMode(emitModeStr);
        this.defaultPodId = defaultPodId;
        log.info("VitoOutboxPublisher initialized with emit-mode={}, default-pod-id={}", emitMode, defaultPodId);
    }

    @Scheduled(fixedDelayString = "${vito.outbox.poll-interval-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        List<EventOutboxEntity> events = outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        for (EventOutboxEntity event : events) {
            try {
                boolean emitted = false;

                // Legacy emit
                if (emitMode == EmitMode.LEGACY_ONLY || emitMode == EmitMode.DUAL) {
                    String legacyTopic = resolveLegacyTopic(event.getAggregateType());
                    kafkaTemplate.send(legacyTopic, event.getAggregateId(), event.getPayload());
                    emitted = true;
                }

                // V1.1 emit — wrap in EventEnvelope
                if (emitMode == EmitMode.V1_1_ONLY || emitMode == EmitMode.DUAL) {
                    emitV11(event);
                    emitted = true;
                }

                if (emitted) {
                    event.setPublishedAt(OffsetDateTime.now());
                    outboxRepository.save(event);
                }
            } catch (Exception e) {
                log.error("Failed to publish event {} to Kafka: {}", event.getId(), e.getMessage());
                // Will be retried on next poll
                break;
            }
        }
    }

    /**
     * Emit v1.1 EventEnvelope to impilo.vito.* topic.
     */
    private void emitV11(EventOutboxEntity event) throws JsonProcessingException {
        String v11Topic = resolveV11Topic(event.getAggregateType());
        String v11EventType = resolveV11EventType(event.getAggregateType(), event.getEventType());

        String tenantId = event.getTenantId() != null ? event.getTenantId() : "unknown";
        String podId = event.getPodId() != null ? event.getPodId() : defaultPodId;
        String correlationId = event.getCorrelationId() != null
                ? event.getCorrelationId() : UUID.randomUUID().toString();
        String idempotencyKey = event.getIdempotencyKey() != null
                ? event.getIdempotencyKey() : String.valueOf(event.getId());

        // Parse the raw payload into a map for the envelope
        Map<String, Object> payloadMap;
        try {
            payloadMap = MAPPER.readValue(event.getPayload(), new TypeReference<>() {});
        } catch (Exception e) {
            // If payload is not valid JSON, wrap it as a string value
            payloadMap = Map.of("raw", event.getPayload());
        }

        OffsetDateTime now = OffsetDateTime.now();
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID().toString(),
                v11EventType,
                1,
                correlationId,
                event.getId() != null ? event.getId().toString() : UUID.randomUUID().toString(),
                idempotencyKey,
                PRODUCER,
                tenantId,
                podId,
                event.getCreatedAt() != null ? event.getCreatedAt() : now,
                now,
                resolveSubjectType(event.getAggregateType()),
                event.getAggregateId(),
                payloadMap,
                Map.of("partition_key", event.getAggregateId())
        );

        String envelopeJson = serializeEnvelope(envelope);
        kafkaTemplate.send(v11Topic, envelope.partitionKey(), envelopeJson);
    }

    // ---------- Legacy topic routing (unchanged) ----------

    private String resolveLegacyTopic(String aggregateType) {
        return switch (aggregateType) {
            case "CLIENT" -> "vito.identity";
            case "ALIAS" -> "vito.alias";
            case "SMART_CARD" -> "vito.cards";
            case "WALLET" -> "vito.wallet";
            case "ISSUANCE" -> "vito.issuance";
            case "MERGE", "DEDUP" -> "vito.dedup";
            case "PROVISIONAL" -> "vito.offline";
            case "PICKUP" -> "vito.pickup";
            case "PRINT_JOB" -> "vito.print";
            default -> "vito.events";
        };
    }

    // ---------- V1.1 topic routing ----------

    private String resolveV11Topic(String aggregateType) {
        return switch (aggregateType) {
            case "CLIENT" -> "impilo.vito.identity";
            case "ALIAS" -> "impilo.vito.alias";
            case "SMART_CARD" -> "impilo.vito.cards";
            case "WALLET" -> "impilo.vito.wallet";
            case "ISSUANCE" -> "impilo.vito.issuance";
            case "MERGE", "DEDUP" -> "impilo.vito.dedup";
            case "PROVISIONAL" -> "impilo.vito.offline";
            case "PICKUP" -> "impilo.vito.pickup";
            case "PRINT_JOB" -> "impilo.vito.print";
            default -> "impilo.vito.events";
        };
    }

    /**
     * Map legacy event type to v1.1 dotted format:
     *   "impilo.vito.<aggregate_lower>.<event_suffix>.v1"
     */
    private String resolveV11EventType(String aggregateType, String eventType) {
        String aggLower = aggregateType.toLowerCase().replace("_", ".");
        // Extract the event suffix from the legacy event type
        // Legacy: "vito.merge.executed" → suffix: "executed"
        // Legacy: "IDENTITY_CREATED" → suffix: "created"
        String suffix = extractEventSuffix(eventType);
        return "impilo.vito." + aggLower + "." + suffix + ".v1";
    }

    private String extractEventSuffix(String eventType) {
        if (eventType == null) return "unknown";
        // Handle dotted format: "vito.merge.executed" → "executed"
        int lastDot = eventType.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < eventType.length() - 1) {
            return eventType.substring(lastDot + 1).toLowerCase();
        }
        // Handle underscore format: "IDENTITY_CREATED" → "created"
        int lastUnderscore = eventType.lastIndexOf('_');
        if (lastUnderscore >= 0 && lastUnderscore < eventType.length() - 1) {
            return eventType.substring(lastUnderscore + 1).toLowerCase();
        }
        return eventType.toLowerCase();
    }

    private String resolveSubjectType(String aggregateType) {
        return switch (aggregateType) {
            case "CLIENT" -> "Patient";
            case "ALIAS" -> "Alias";
            case "SMART_CARD" -> "SmartCard";
            case "WALLET" -> "Wallet";
            case "ISSUANCE" -> "IssuanceRequest";
            case "MERGE" -> "MergeHistory";
            case "DEDUP" -> "DedupCase";
            case "PROVISIONAL" -> "ProvisionalId";
            case "PICKUP" -> "DelegatedPickup";
            case "PRINT_JOB" -> "PrintJob";
            default -> aggregateType;
        };
    }

    // ---------- Serialization ----------

    private String serializeEnvelope(EventEnvelope envelope) throws JsonProcessingException {
        // Build as LinkedHashMap to maintain field order
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("event_id", envelope.eventId());
        map.put("event_type", envelope.eventType());
        map.put("schema_version", envelope.schemaVersion());
        map.put("correlation_id", envelope.correlationId());
        map.put("causation_id", envelope.causationId());
        map.put("idempotency_key", envelope.idempotencyKey());
        map.put("producer", envelope.producer());
        map.put("tenant_id", envelope.tenantId());
        map.put("pod_id", envelope.podId());
        map.put("occurred_at", envelope.occurredAt().toString());
        map.put("emitted_at", envelope.emittedAt().toString());
        map.put("subject_type", envelope.subjectType());
        map.put("subject_id", envelope.subjectId());
        map.put("payload", envelope.payload());
        map.put("meta", envelope.meta());
        return MAPPER.writeValueAsString(map);
    }

    private static EmitMode parseEmitMode(String value) {
        if (value == null || value.isBlank()) return EmitMode.DUAL;
        try {
            return EmitMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid emit-mode '{}', defaulting to DUAL", value);
            return EmitMode.DUAL;
        }
    }
}
