package zw.gov.mohcc.impilo.mvumo.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.mvumo.persistence.EventOutboxEntity;
import zw.gov.mohcc.impilo.mvumo.persistence.EventOutboxRepository;

import java.time.Instant;
import java.util.List;

/**
 * Publishes {@code mvumo.event_outbox} rows to Kafka and marks them published.
 */
@Component
@ConditionalOnProperty(name = "mvumo.outbox.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(KafkaTemplate.class)
public class MvumoOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(MvumoOutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public MvumoOutboxPublisher(
            EventOutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${mvumo.outbox.topic:platform.mvumo.events}") String topic) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Scheduled(fixedDelayString = "${mvumo.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPending() {
        List<EventOutboxEntity> batch = outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        if (batch.isEmpty()) {
            return;
        }
        for (EventOutboxEntity row : batch) {
            try {
                String envelope =
                        objectMapper.createObjectNode()
                                .put("eventType", row.getEventType())
                                .put("aggregateType", row.getAggregateType())
                                .put("aggregateId", row.getAggregateId())
                                .put("occurredAt", Instant.now().toString())
                                .set("payload", objectMapper.readTree(row.getPayload()))
                                .toString();
                String key = row.getAggregateId() != null ? row.getAggregateId() : "mvumo-" + row.getId();
                kafkaTemplate.send(topic, key, envelope);
                row.setPublishedAt(Instant.now());
                outboxRepository.save(row);
                log.debug("Published mvumo outbox id={} type={} topic={}", row.getId(), row.getEventType(), topic);
            } catch (Exception e) {
                log.error("Failed to publish mvumo outbox id={}: {}", row.getId(), e.getMessage());
                break;
            }
        }
    }
}
