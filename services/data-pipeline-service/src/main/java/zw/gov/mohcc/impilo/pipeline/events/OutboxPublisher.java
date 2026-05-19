package zw.gov.mohcc.impilo.pipeline.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pipeline.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pipeline.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Scheduled outbox publisher that polls for unpublished events and
 * publishes them to Kafka topics.
 *
 * <p>Implements the transactional outbox pattern for at-least-once delivery.</p>
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 100;

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final boolean reportingAggregateEmitEnabled;
    private final String reportingAggregateTopic;

    public OutboxPublisher(EventOutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           @Value("${pipeline.outbox.reporting-aggregate-emit-enabled:true}") boolean reportingAggregateEmitEnabled,
                           @Value("${pipeline.outbox.reporting-aggregate-topic:analytics.reporting.aggregate}") String reportingAggregateTopic) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.reportingAggregateEmitEnabled = reportingAggregateEmitEnabled;
        this.reportingAggregateTopic = reportingAggregateTopic;
    }

    @Scheduled(fixedDelayString = "${pipeline.outbox.poll-interval-ms:500}")
    @Transactional
    public void publishPendingEvents() {
        List<EventOutboxEntity> pending = outboxRepository
                .findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        if (pending.isEmpty()) {
            return;
        }

        List<EventOutboxEntity> batch = pending.size() > BATCH_SIZE
                ? pending.subList(0, BATCH_SIZE)
                : pending;

        int successCount = 0;
        for (EventOutboxEntity event : batch) {
            try {
                String topic = routeTopic(event.getEventType());
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload());
                String secondaryTopic = resolveSecondaryTopic(event.getEventType());
                if (secondaryTopic != null && !secondaryTopic.equals(topic)) {
                    kafkaTemplate.send(secondaryTopic, event.getAggregateId(), event.getPayload());
                }

                event.setPublishedAt(OffsetDateTime.now());
                outboxRepository.save(event);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to publish outbox event {} (type={}) to Kafka: {}",
                        event.getId(), event.getEventType(), e.getMessage(), e);
            }
        }

        if (successCount > 0) {
            log.info("Published {}/{} outbox events to Kafka", successCount, batch.size());
        }
    }

    /**
     * Route event types to Kafka topics.
     */
    static String routeTopic(String eventType) {
        if (eventType == null) {
            return "impilo.pipeline.events";
        }

        return switch (eventType) {
            case "EVENT_INGESTED" -> "impilo.pipeline.event.ingested.v1";
            case "WATERMARK_UPDATED" -> "impilo.pipeline.watermark.updated.v1";
            default -> "impilo.pipeline.events";
        };
    }

    private String resolveSecondaryTopic(String eventType) {
        if (!reportingAggregateEmitEnabled || eventType == null) {
            return null;
        }
        return switch (eventType) {
            case "EVENT_INGESTED" -> reportingAggregateTopic;
            default -> null;
        };
    }
}
