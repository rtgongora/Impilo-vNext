package zw.gov.mohcc.impilo.ndila.core.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.ndila.domain.NdilaOutboxEventEntity;
import zw.gov.mohcc.impilo.ndila.repository.NdilaOutboxRepository;

import java.util.UUID;

/**
 * Outbox publisher — every Ndila event is written here before being relayed to
 * Kafka by an external outbox relay. This is the canonical Impilo pattern.
 *
 * <p>Published topics include: location lifecycle, route calculations and
 * deviations, geofence lifecycle and breaches, catchment lifecycle and overlaps,
 * tracking lifecycle and outside-zone events, provider selection / fallback /
 * failure / quota / cache events, and intelligence-signal events.
 */
@Service
public class NdilaOutboxPublisher {

    private final NdilaOutboxRepository repository;
    private final ObjectMapper objectMapper;

    public NdilaOutboxPublisher(NdilaOutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void publish(NdilaEvent event) {
        NdilaOutboxEventEntity row = new NdilaOutboxEventEntity();
        row.setAggregateType(event.aggregateType);
        row.setAggregateId(event.aggregateId);
        row.setEventType(event.eventType);
        row.setSchemaVersion("1.0");
        row.setCorrelationId(event.correlationId);
        row.setIdempotencyKey(event.eventType + ":" + UUID.randomUUID());
        row.setTenantId(event.tenantId);
        row.setSubjectId(event.aggregateId);
        row.setSubjectType(event.aggregateType);
        try {
            row.setPayloadJson(objectMapper.writeValueAsString(event.payload));
        } catch (JsonProcessingException e) {
            row.setPayloadJson("{\"error\":\"payload_serialization_failed\"}");
        }
        repository.save(row);
    }

    public static class NdilaEvent {
        public final String eventType;
        public final String aggregateType;
        public final String aggregateId;
        public final UUID tenantId;
        public final UUID correlationId;
        public final Object payload;

        public NdilaEvent(String eventType, String aggregateType, String aggregateId,
                          UUID tenantId, UUID correlationId, Object payload) {
            this.eventType = eventType;
            this.aggregateType = aggregateType;
            this.aggregateId = aggregateId;
            this.tenantId = tenantId;
            this.correlationId = correlationId;
            this.payload = payload;
        }
    }
}
