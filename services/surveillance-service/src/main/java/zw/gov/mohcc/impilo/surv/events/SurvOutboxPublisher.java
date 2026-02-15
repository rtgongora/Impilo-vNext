package zw.gov.mohcc.impilo.surv.events;

import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.surv.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.surv.persistence.repository.EventOutboxRepository;

@Component
public class SurvOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(SurvOutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public SurvOutboxPublisher(EventOutboxRepository outboxRepository,
                               KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${surv.outbox.poll-interval-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        List<EventOutboxEntity> events =
                outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        for (EventOutboxEntity event : events) {
            try {
                String topic = resolveTopic(event.getEventType());
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload());
                event.setPublishedAt(OffsetDateTime.now());
                outboxRepository.save(event);
            } catch (Exception e) {
                log.error("Failed to publish outbox event id={}: {}", event.getId(), e.getMessage());
                break;
            }
        }
    }

    static String resolveTopic(String eventType) {
        return switch (eventType) {
            case "SIGNAL_CREATED" -> "impilo.surv.signal.created.v1";
            case "SIGNAL_HIT" -> "impilo.surv.signal.hit.v1";
            case "CASE_OPENED" -> "impilo.surv.case.opened.v1";
            default -> "impilo.surv.unknown";
        };
    }
}
