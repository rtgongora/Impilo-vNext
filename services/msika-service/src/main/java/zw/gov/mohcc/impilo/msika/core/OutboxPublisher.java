package zw.gov.mohcc.impilo.msika.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msika.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.msika.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(EventOutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${msika.outbox.poll-interval-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        List<EventOutboxEntity> events =
                outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        if (events.isEmpty()) return;

        log.debug("Publishing {} pending outbox events", events.size());

        int published = 0;
        for (EventOutboxEntity event : events) {
            try {
                String topic = resolveTopic(event.getEventType());
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload());
                event.setPublishedAt(OffsetDateTime.now());
                outboxRepository.save(event);
                published++;
                log.debug("Published event {} (type={}) to topic {}",
                        event.getId(), event.getEventType(), topic);
            } catch (Exception e) {
                log.error("Failed to publish event {} (type={}) to Kafka: {}",
                        event.getId(), event.getEventType(), e.getMessage());
                break;
            }
        }

        if (published > 0) {
            log.info("Published {}/{} outbox events to Kafka", published, events.size());
        }
    }

    String resolveTopic(String eventType) {
        if (eventType == null) return "msika.core.events";
        return switch (eventType) {
            case "CATALOG_PUBLISHED", "CATALOG_APPROVED" -> "msika.core.catalog.published";
            case "ITEM_CREATED", "ITEM_UPDATED", "ITEM_DELETED" -> "msika.core.item.changed";
            case "MAPPING_APPROVED" -> "msika.core.mapping.approved";
            default -> "msika.core.events";
        };
    }
}
