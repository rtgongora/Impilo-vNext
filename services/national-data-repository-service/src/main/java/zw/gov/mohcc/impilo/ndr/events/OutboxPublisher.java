package zw.gov.mohcc.impilo.ndr.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.ndr.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.ndr.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(EventOutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${ndr.outbox.poll-interval-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        List<EventOutboxEntity> pending =
                outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        for (EventOutboxEntity event : pending) {
            try {
                String topic = routeTopic(event.getEventType());
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload());
                event.setPublishedAt(OffsetDateTime.now());
                outboxRepository.save(event);
            } catch (Exception e) {
                log.warn("Failed to publish outbox event id={}: {}",
                        event.getId(), e.getMessage());
                break;
            }
        }

        if (!pending.isEmpty()) {
            log.debug("Published {} outbox events", pending.size());
        }
    }

    static String routeTopic(String eventType) {
        return switch (eventType) {
            case "DATASET_CREATED" -> "impilo.ndr.dataset.created.v1";
            case "DATASET_VERSIONED" -> "impilo.ndr.dataset.versioned.v1";
            default -> "impilo.ndr.events";
        };
    }
}
