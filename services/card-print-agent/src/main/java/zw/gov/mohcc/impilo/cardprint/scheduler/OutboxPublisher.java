package zw.gov.mohcc.impilo.cardprint.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    public OutboxPublisher(EventOutboxRepository eventOutboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           ObjectMapper objectMapper) {
        this.eventOutboxRepository = eventOutboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
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
                String payloadJson = objectMapper.writeValueAsString(event.getPayload());
                kafkaTemplate.send(topic, event.getAggregateId(), payloadJson);
                event.setPublishedAt(OffsetDateTime.now());
                eventOutboxRepository.save(event);
                log.debug("Published outbox event: id={}, type={}, topic={}",
                        event.getId(), event.getEventType(), topic);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize outbox event payload: id={}, type={}",
                        event.getId(), event.getEventType(), e);
                break;
            } catch (Exception e) {
                log.error("Failed to publish outbox event: id={}, type={}",
                        event.getId(), event.getEventType(), e);
                break;
            }
        }
    }
}
