package zw.gov.mohcc.impilo.obs.events;

import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.obs.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.obs.persistence.repository.EventOutboxRepository;

@Component
public class ObsOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(ObsOutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public ObsOutboxPublisher(EventOutboxRepository outboxRepository,
                              KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${obs.outbox.poll-interval-ms:5000}")
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
            case "DASHBOARD_CREATED" -> "impilo.obs.dashboard.created.v1";
            case "ALERT_RULE_CREATED" -> "impilo.obs.alert-rule.created.v1";
            default -> "impilo.obs.unknown";
        };
    }
}
