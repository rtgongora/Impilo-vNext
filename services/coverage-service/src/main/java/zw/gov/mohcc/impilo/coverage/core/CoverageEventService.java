package zw.gov.mohcc.impilo.coverage.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.coverage.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.coverage.repository.OutboxEventRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Emits v1.1 domain events via the transactional outbox.
 * Event types follow: impilo.coverage.{entity}.{action}.v1
 */
@Service
public class CoverageEventService {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final OutboxEventRepository outboxRepository;

    public CoverageEventService(OutboxEventRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    /**
     * Emit a CREATE event with full entity state.
     */
    public void emitCreated(String entityName, String entityId,
                            UUID correlationId, UUID tenantId, String podId,
                            Object entityState) {
        String eventType = "impilo.coverage." + entityName + ".created.v1";
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("change_type", "CREATE");
        data.put("entity_version", 1);
        data.put("state", entityState);
        data.put("meta", buildMeta(correlationId));

        emit(entityName, entityId, eventType, correlationId, tenantId, podId, data);
    }

    /**
     * Emit a status transition event (submitted, approved, denied, etc.)
     */
    public void emitStatusChange(String entityName, String entityId, String action,
                                  UUID correlationId, UUID tenantId, String podId,
                                  Map<String, Object> changedFields) {
        String eventType = "impilo.coverage." + entityName + "." + action + ".v1";
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("change_type", "UPDATE");
        data.put("changed_fields", changedFields);
        data.put("meta", buildMeta(correlationId));

        emit(entityName, entityId, eventType, correlationId, tenantId, podId, data);
    }

    private void emit(String aggregateType, String aggregateId, String eventType,
                      UUID correlationId, UUID tenantId, String podId,
                      Map<String, Object> data) {
        String payloadJson;
        try {
            // Build full EventEnvelope in payload_json
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("event_id", UUID.randomUUID().toString());
            envelope.put("event_type", eventType);
            envelope.put("schema_version", "1.0");
            envelope.put("correlation_id", correlationId != null ? correlationId.toString() : null);
            envelope.put("causation_id", null);
            envelope.put("producer", "coverage-service");
            envelope.put("tenant_id", tenantId != null ? tenantId.toString() : null);
            envelope.put("pod_id", podId);
            envelope.put("subject_id", aggregateId);
            envelope.put("subject_type", capitalize(aggregateType));
            envelope.put("data", data);
            envelope.put("meta", Map.of("partition_key", (tenantId != null ? tenantId : "") + ":" + aggregateId));

            payloadJson = MAPPER.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }

        OutboxEventEntity outbox = OutboxEventEntity.create(
                capitalize(aggregateType), aggregateId,
                eventType, correlationId,
                tenantId, podId,
                aggregateId, capitalize(aggregateType),
                payloadJson);

        outboxRepository.save(outbox);
    }

    private Map<String, Object> buildMeta(UUID correlationId) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("partition_key", correlationId != null ? correlationId.toString() : UUID.randomUUID().toString());
        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put("evidence", "bounded-stale-class-b");
        meta.put("decision", decision);
        return meta;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
