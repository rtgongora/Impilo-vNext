package zw.gov.mohcc.impilo.orgregistry.events;

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
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.EventOutboxRepository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Publishes transactional outbox rows to Kafka when
 * {@code impilo.orgregistry.kafka-events-enabled=true}.
 * Pattern copied from workforce-governance {@code GovernanceOutboxPublisher}.
 */
@Component
@ConditionalOnProperty(name = "impilo.orgregistry.kafka-events-enabled", havingValue = "true")
public class OrgRegistryOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrgRegistryOutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${impilo.orgregistry.outbox-topic:impilo.org-registry.events}")
    private String topic;

    public OrgRegistryOutboxPublisher(EventOutboxRepository outboxRepository,
                                      KafkaTemplate<String, String> kafkaTemplate,
                                      ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${impilo.orgregistry.outbox.poll-interval-ms:2000}")
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
                    envelope.put("tenantId", row.getTenantId().toString());
                }
                if (row.getCorrelationId() != null) {
                    envelope.put("correlationId", row.getCorrelationId().toString());
                }
                envelope.put("occurredAt", Instant.now().toString());
                envelope.set("payload", objectMapper.readTree(row.getPayloadJson()));

                String key = row.getTenantId() != null ? row.getTenantId().toString() : row.getAggregateId();
                kafkaTemplate.send(topic, key, objectMapper.writeValueAsString(envelope));
                row.setPublishedAt(OffsetDateTime.now());
                outboxRepository.save(row);
            } catch (Exception e) {
                log.warn("Failed to publish org-registry outbox id={}: {}", row.getId(), e.getMessage());
                row.setPublishError(e.getMessage());
                row.setRetryCount(row.getRetryCount() + 1);
                outboxRepository.save(row);
            }
        }
    }
}
