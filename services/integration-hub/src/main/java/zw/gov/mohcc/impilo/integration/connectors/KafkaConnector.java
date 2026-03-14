package zw.gov.mohcc.impilo.integration.connectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.integration.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.integration.repository.OutboxEventRepository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Component
public class KafkaConnector implements Connector {

    private static final Logger log = LoggerFactory.getLogger(KafkaConnector.class);
    private static final String DEFAULT_TOPIC = "integration.hub.dispatch.requested";

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public KafkaConnector(OutboxEventRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public ConnectorType type() {
        return ConnectorType.KAFKA;
    }

    @Override
    public ConnectorResult execute(ConnectorRequest request) {
        try {
            String topic = request.targetUrl() != null && !request.targetUrl().isBlank()
                    ? request.targetUrl()
                    : DEFAULT_TOPIC;

            String correlationId = request.headers() != null
                    ? request.headers().getOrDefault("X-Correlation-ID", UUID.randomUUID().toString())
                    : UUID.randomUUID().toString();

            String tenantId = request.headers() != null
                    ? request.headers().getOrDefault("X-Tenant-ID", "unknown")
                    : "unknown";

            String podId = request.headers() != null
                    ? request.headers().getOrDefault("X-Pod-ID", "unknown")
                    : "unknown";

            Map<String, Object> payload = Map.of(
                    "routeId", request.routeId() != null ? request.routeId() : "",
                    "method", request.method() != null ? request.method() : "POST",
                    "path", request.path() != null ? request.path() : "",
                    "body", request.body() != null ? request.body() : "",
                    "topic", topic
            );

            OutboxEventEntity outbox = new OutboxEventEntity();
            outbox.setTenantId(tenantId);
            outbox.setPodId(podId);
            outbox.setCorrelationId(correlationId);
            outbox.setIdempotencyKey("kafka-dispatch-" + request.routeId() + "-" + UUID.randomUUID());
            outbox.setEventType(topic);
            outbox.setSchemaVersion(1);
            outbox.setOccurredAt(OffsetDateTime.now());
            outbox.setPayloadJson(objectMapper.writeValueAsString(payload));

            outboxRepository.save(outbox);

            log.info("KafkaConnector wrote outbox event for topic={}, route={}", topic, request.routeId());

            return ConnectorResult.success(202, "Queued to outbox for topic: " + topic);
        } catch (Exception e) {
            log.error("KafkaConnector error: route={}, error={}", request.routeId(), e.getMessage(), e);
            return ConnectorResult.error("Kafka dispatch failed: " + e.getMessage());
        }
    }
}
