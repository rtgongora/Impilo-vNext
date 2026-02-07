package zw.gov.mohcc.impilo.cardprint.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.cardprint.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.cardprint.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final EventOutboxRepository eventOutboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(EventOutboxRepository eventOutboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate) {
        this.eventOutboxRepository = eventOutboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${card-print.outbox.poll-interval-ms:3000}")
    @Transactional
    public void publishPendingEvents() {
        List<EventOutboxEntity> events = eventOutboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        if (events.isEmpty()) return;

        log.debug("Publishing {} outbox events to Kafka", events.size());

        for (EventOutboxEntity event : events) {
            try {
                String topic = event.getEventType();
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload());
                event.setPublishedAt(OffsetDateTime.now());
                eventOutboxRepository.save(event);
                log.debug("Published outbox event: id={}, type={}, topic={}",
                        event.getId(), event.getEventType(), topic);
            } catch (Exception e) {
                log.error("Failed to publish outbox event: id={}, type={}",
                        event.getId(), event.getEventType(), e);
                break;
            }
        }
    }
}
