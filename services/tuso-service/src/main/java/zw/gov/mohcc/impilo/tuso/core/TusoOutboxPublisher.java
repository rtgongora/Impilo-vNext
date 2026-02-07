package zw.gov.mohcc.impilo.tuso.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tuso.config.TusoProperties;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Polls the event_outbox table and publishes pending events to Kafka.
 *
 * <p>Uses the transactional outbox pattern: events are first written to the outbox table
 * within the same transaction as the domain change, then this publisher picks them up
 * and sends them to Kafka. Successfully published events are marked with a publishedAt timestamp.</p>
 */
@Service
public class TusoOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(TusoOutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TusoProperties tusoProperties;
    private final ObjectMapper objectMapper;

    public TusoOutboxPublisher(EventOutboxRepository outboxRepository,
                                KafkaTemplate<String, String> kafkaTemplate,
                                TusoProperties tusoProperties,
                                ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.tusoProperties = tusoProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Scheduled task that polls for pending outbox events and publishes them to Kafka.
     * Runs at the interval configured in tuso.outbox.poll-interval-ms.
     */
    @Scheduled(fixedDelayString = "${tuso.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        List<EventOutboxEntity> pending = outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        if (pending.isEmpty()) {
            return;
        }

        log.debug("Publishing {} pending outbox events", pending.size());
        String topicPrefix = tusoProperties.getOutbox().getKafkaTopicPrefix();

        int successCount = 0;
        int failCount = 0;

        for (EventOutboxEntity event : pending) {
            try {
                String topic = topicPrefix + "." + event.getEventType();
                String key = event.getAggregateType() + ":" + event.getAggregateId();
                String payloadJson = serializePayload(event.getPayload());

                kafkaTemplate.send(topic, key, payloadJson);

                event.setPublishedAt(Instant.now());
                outboxRepository.save(event);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to publish outbox event {}: {}", event.getId(), e.getMessage());
                failCount++;
                // Event remains with publishedAt=null and will be retried on next poll
            }
        }

        if (successCount > 0 || failCount > 0) {
            log.info("Outbox publish complete: {} sent, {} failed", successCount, failCount);
        }
    }

    private String serializePayload(Map<String, Object> payload) {
        if (payload == null) {
            return "{}";
        }
        // If it was stored via the String convenience setter, extract the raw value
        if (payload.size() == 1 && payload.containsKey("raw")) {
            Object raw = payload.get("raw");
            return raw != null ? raw.toString() : "{}";
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize payload, using toString: {}", e.getMessage());
            return payload.toString();
        }
    }
}
