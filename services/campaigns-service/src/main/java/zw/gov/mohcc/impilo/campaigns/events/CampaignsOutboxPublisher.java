package zw.gov.mohcc.impilo.campaigns.events;

import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.campaigns.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.campaigns.persistence.repository.EventOutboxRepository;

@Component
public class CampaignsOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(CampaignsOutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public CampaignsOutboxPublisher(EventOutboxRepository outboxRepository,
                                     KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${campaigns.outbox.poll-interval-ms:2000}")
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
            case "CAMPAIGN_CREATED" -> "impilo.campaigns.created.v1";
            case "ENROLLMENT_CREATED" -> "impilo.campaigns.enrolled.v1";
            case "CAMPAIGN_DISPATCHED" -> "impilo.campaigns.dispatched.v1";
            default -> "impilo.campaigns.unknown";
        };
    }
}
