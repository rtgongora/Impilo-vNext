package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Writes event outbox rows within the same transaction as the domain write.
 * Conforms to EventEnvelope schema_version >= 1 with meta.partition_key.
 */
@Service
public class OutboxService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public OutboxService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void writeOutboxEvent(
            String eventType,
            String correlationId,
            String causationId,
            String idempotencyKey,
            String tenantId,
            String podId,
            String subjectType,
            String subjectId,
            Map<String, Object> payload,
            Map<String, Object> meta) {

        String eventId = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();

        String partitionKey = tenantId + ":" + subjectType + ":" + subjectId;

        Map<String, Object> fullMeta = new java.util.HashMap<>(meta != null ? meta : Map.of());
        fullMeta.put("partition_key", partitionKey);

        String payloadJson;
        String metaJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
            metaJson = objectMapper.writeValueAsString(fullMeta);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox event payload", e);
        }

        jdbc.update("""
            INSERT INTO event_outbox
                (event_id, event_type, schema_version, correlation_id, causation_id,
                 idempotency_key, producer, tenant_id, pod_id, subject_type, subject_id,
                 payload, meta, occurred_at, partition_key)
            VALUES (?, ?, 1, ?, ?, ?, 'experience-bff', ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
            """,
                UUID.fromString(eventId), eventType, correlationId, causationId,
                idempotencyKey, tenantId, podId, subjectType, subjectId,
                payloadJson, metaJson, now, partitionKey);
    }
}
