package zw.gov.mohcc.impilo.tshepo.identity.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.repository.EventOutboxRepository;

import java.time.Instant;
import java.util.List;

/**
 * Polls the event_outbox table and publishes unpublished events to Kafka.
 *
 * <p>Uses the transactional outbox pattern for reliable, at-least-once event
 * delivery. Events are ordered by creation time and published sequentially.
 * If any event fails to publish, processing stops to maintain ordering.</p>
 */
@Component
public class IdentityOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(IdentityOutboxPublisher.class);

    private final EventOutboxRepository outboxRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public IdentityOutboxPublisher(EventOutboxRepository outboxRepo,
                                    KafkaTemplate<String, String> kafkaTemplate,
                                    @Value("${tshepo.outbox.kafka-topic:trust.tshepo.identity.events}") String topic) {
        this.outboxRepo = outboxRepo;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Scheduled(fixedDelayString = "${tshepo.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        List<EventOutboxEntity> pending = outboxRepo.findUnpublished();
        for (EventOutboxEntity event : pending) {
            try {
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload());
                event.setPublishedAt(Instant.now());
                outboxRepo.save(event);
            } catch (Exception e) {
                log.error("Failed to publish outbox event id={}: {}", event.getId(), e.getMessage());
                break; // Stop processing to maintain ordering
            }
        }
    }
}
