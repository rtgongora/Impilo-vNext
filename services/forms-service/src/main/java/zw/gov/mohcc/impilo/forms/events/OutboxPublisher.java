package zw.gov.mohcc.impilo.forms.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.forms.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.forms.repository.OutboxEventRepository;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(OutboxEventRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${forms.outbox.poll-interval-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEventEntity> events =
                outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        for (OutboxEventEntity event : events) {
            try {
                String topic = resolveTopic(event.getEventType());
                kafkaTemplate.send(topic, event.getTenantId(), event.getPayloadJson());
                event.setPublishedAt(OffsetDateTime.now());
                outboxRepository.save(event);
            } catch (Exception e) {
                log.error("Failed to publish outbox event {} to Kafka: {}",
                        event.getId(), e.getMessage());
                break;
            }
        }
    }

    private String resolveTopic(String eventType) {
        if (eventType.startsWith("impilo.forms.form.")) {
            return "impilo.forms.form";
        }
        if (eventType.startsWith("impilo.forms.version.")) {
            return "impilo.forms.version";
        }
        return "impilo.forms.events";
    }
}
