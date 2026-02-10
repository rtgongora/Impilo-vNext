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
import zw.gov.mohcc.impilo.sharedkernel.events.EmitMode;
import zw.gov.mohcc.impilo.sharedkernel.events.EventEnvelope;
import zw.gov.mohcc.impilo.vito.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * VITO Event Outbox Publisher — reliable Kafka delivery with v1.1 DualEmitPolicy.
 *
 * Polls the event_outbox table for unpublished events and publishes
 * them to Kafka. Uses the outbox pattern to ensure at-least-once
 * delivery with transactional consistency.
 *
 * <h3>Emit mode precedence (via {@link VitoEventMapper#resolveEmitMode})</h3>
 * <ol>
 *   <li>{@code EMIT_MODE} system property (highest priority)</li>
 *   <li>{@code EMIT_MODE} environment variable</li>
 *   <li>{@code vito.v11.emit-mode} from application.yml</li>
 *   <li>Default: {@link EmitMode#DUAL}</li>
 * </ol>
 *
 * Uses canonical types from shared-kernel-java:
 * {@link EventEnvelope}, {@link EmitMode}.
 * VITO-specific topic/type mapping delegated to {@link VitoEventMapper}.
 */
@Component
public class VitoOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(VitoOutboxPublisher.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final EmitMode emitMode;

    public VitoOutboxPublisher(EventOutboxRepository outboxRepository,
                                KafkaTemplate<String, String> kafkaTemplate,
                                @Value("${vito.v11.emit-mode:#{null}}") String ymlEmitMode) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.emitMode = VitoEventMapper.resolveEmitMode(ymlEmitMode);
        log.info("VitoOutboxPublisher initialized with effective emit-mode={}", emitMode);
    }

    @Scheduled(fixedDelayString = "${vito.outbox.poll-interval-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        List<EventOutboxEntity> events = outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        for (EventOutboxEntity event : events) {
            try {
                // Legacy emit
                if (VitoEventMapper.emitLegacy(emitMode)) {
                    String legacyTopic = VitoEventMapper.resolveLegacyTopic(event.getAggregateType());
                    kafkaTemplate.send(legacyTopic, event.getAggregateId(), event.getPayload());
                }

                // V1.1 emit — wrap in shared-kernel EventEnvelope
                if (VitoEventMapper.emitV11(emitMode)) {
                    emitV11(event);
                }

                event.setPublishedAt(OffsetDateTime.now());
                outboxRepository.save(event);
            } catch (Exception e) {
                log.error("Failed to publish event {} to Kafka: {}", event.getId(), e.getMessage());
                break;
            }
        }
    }

    private void emitV11(EventOutboxEntity event) throws JsonProcessingException {
        String v11Topic = VitoEventMapper.resolveV11Topic(event.getAggregateType());

        // Parse the raw payload into a map for the envelope
        Map<String, Object> payloadMap;
        try {
            payloadMap = MAPPER.readValue(event.getPayload(), new TypeReference<>() {});
        } catch (Exception e) {
            payloadMap = Map.of("raw", event.getPayload());
        }

        // Build canonical EventEnvelope via VitoEventMapper (delegates to shared-kernel Builder)
        EventEnvelope envelope = VitoEventMapper.buildEnvelope(
                event.getAggregateType(),
                event.getAggregateId(),
                event.getEventType(),
                payloadMap,
                event.getTenantId(),
                event.getPodId(),
                event.getCorrelationId(),
                event.getIdempotencyKey(),
                event.getId(),
                event.getCreatedAt()
        );

        String envelopeJson = serializeEnvelope(envelope);
        kafkaTemplate.send(v11Topic, envelope.partitionKey(), envelopeJson);
    }

    private String serializeEnvelope(EventEnvelope envelope) throws JsonProcessingException {
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
}
