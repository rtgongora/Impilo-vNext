package zw.gov.mohcc.impilo.varapi.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.varapi.config.VarapiProperties;
import zw.gov.mohcc.impilo.varapi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;

import java.time.Instant;
import java.util.List;

@Component
public class VarapiOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(VarapiOutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final VarapiProperties properties;

    public VarapiOutboxPublisher(EventOutboxRepository outboxRepository,
                                  KafkaTemplate<String, String> kafkaTemplate,
                                  VarapiProperties properties) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${varapi.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        List<EventOutboxEntity> pending =
                outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        if (pending.isEmpty()) return;

        log.debug("Publishing {} pending outbox events", pending.size());

        for (EventOutboxEntity event : pending) {
            String topic = properties.getOutbox().getKafkaTopicPrefix() + "." + event.getEventType();
            try {
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload());
                event.setPublishedAt(Instant.now());
                outboxRepository.save(event);
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
