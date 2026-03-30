package zw.gov.mohcc.impilo.tshepo.offline.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.offline.persistence.EventOutboxEntity;
import zw.gov.mohcc.impilo.tshepo.offline.persistence.EventOutboxRepository;

import java.time.Instant;
import java.util.List;

/**
 * Polls the event_outbox table and publishes unpublished events to Kafka.
 * Uses the transactional outbox pattern for reliable event delivery.
 */
@Component
public class OfflineOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OfflineOutboxPublisher.class);
    private static final String OFFLINE_TOPIC = "trust.tshepo.offline.events";

    private final EventOutboxRepository outboxRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OfflineOutboxPublisher(EventOutboxRepository outboxRepo,
                                   KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepo = outboxRepo;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${tshepo.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        List<EventOutboxEntity> pending = outboxRepo.findUnpublished();
        for (EventOutboxEntity event : pending) {
            try {
                kafkaTemplate.send(OFFLINE_TOPIC, event.getAggregateId(), event.getPayload());
                event.setPublishedAt(Instant.now());
                outboxRepo.save(event);
            } catch (Exception e) {
                log.error("Failed to publish outbox event id={}: {}", event.getId(), e.getMessage());
                break; // Stop processing to maintain ordering
            }
        }
    }
}
