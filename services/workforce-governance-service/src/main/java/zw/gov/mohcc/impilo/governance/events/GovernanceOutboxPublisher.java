package zw.gov.mohcc.impilo.governance.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.governance.persistence.EventOutboxEntity;
import zw.gov.mohcc.impilo.governance.persistence.EventOutboxRepository;

import java.time.Instant;
import java.util.List;

/**
 * Publishes transactional outbox rows to Kafka when {@code impilo.governance.kafka-events-enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "impilo.governance.kafka-events-enabled", havingValue = "true")
public class GovernanceOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(GovernanceOutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${impilo.governance.outbox-topic:impilo.governance.events}")
    private String topic;

    public GovernanceOutboxPublisher(EventOutboxRepository outboxRepository,
                                     KafkaTemplate<String, String> kafkaTemplate,
                                     ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${impilo.governance.outbox-publish-interval-ms:2000}")
    @Transactional
    public void publishBatch() {
        List<EventOutboxEntity> pending = outboxRepository.findTop100ByPublishedAtIsNullOrderByIdAsc();
        if (pending.isEmpty()) {
            return;
        }
        for (EventOutboxEntity row : pending) {
            try {
                ObjectNode envelope = objectMapper.createObjectNode();
                envelope.put("eventType", row.getEventType());
                envelope.put("aggregateType", row.getAggregateType());
                envelope.put("aggregateId", row.getAggregateId());
                if (row.getTenantId() != null) {
                    envelope.put("tenantId", row.getTenantId());
                }
                if (row.getCorrelationId() != null) {
                    envelope.put("correlationId", row.getCorrelationId());
                }
                envelope.put("occurredAt", Instant.now().toString());
                envelope.set("payload", objectMapper.readTree(row.getPayload()));

                String key = row.getTenantId() != null ? row.getTenantId() : row.getAggregateId();
                kafkaTemplate.send(topic, key, objectMapper.writeValueAsString(envelope));
                row.setPublishedAt(Instant.now());
                outboxRepository.save(row);
            } catch (Exception e) {
                log.warn("Failed to publish governance outbox id={}: {}", row.getId(), e.getMessage());
            }
        }
    }
}
