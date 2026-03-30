package zw.gov.mohcc.impilo.tshepo.consent.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.consent.config.ConsentProperties;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.EventOutboxEntity;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.EventOutboxRepository;

import java.time.Instant;
import java.util.List;

/**
 * Polls the event_outbox table and publishes unpublished events to Kafka.
 *
 * <p>Implements the transactional outbox pattern for reliable event delivery.
 * Events are written to the outbox table within the same transaction as consent mutations,
 * then asynchronously polled and published to Kafka by this component.</p>
 *
 * <p>Processing stops on first failure to maintain event ordering.</p>
 */
@Component
public class ConsentOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(ConsentOutboxPublisher.class);
    private static final String REVOCATION_TOPIC = "trust.revocation.consent";
    private static final String REVOCATION_EVENT_TYPE = "impilo.tshepo.consent.revoked.v1";

    private final EventOutboxRepository outboxRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaTemplate<String, Object> trustChannelKafkaTemplate;
    private final ConsentProperties properties;

    public ConsentOutboxPublisher(EventOutboxRepository outboxRepo,
                                   KafkaTemplate<String, String> kafkaTemplate,
                                   @Qualifier("trustChannelKafkaTemplate") KafkaTemplate<String, Object> trustChannelKafkaTemplate,
                                   ConsentProperties properties) {
        this.outboxRepo = outboxRepo;
        this.kafkaTemplate = kafkaTemplate;
        this.trustChannelKafkaTemplate = trustChannelKafkaTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${consent.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        List<EventOutboxEntity> pending = outboxRepo.findUnpublished();
        if (pending.isEmpty()) {
            return;
        }

        String defaultTopic = properties.getOutbox().getKafkaTopic();
        for (EventOutboxEntity event : pending) {
            try {
                if (REVOCATION_EVENT_TYPE.equals(event.getEventType())) {
                    trustChannelKafkaTemplate.send(REVOCATION_TOPIC, event.getAggregateId(), event.getPayload());
                    log.debug("Published revocation event id={} to trust channel topic={}",
                            event.getId(), REVOCATION_TOPIC);
                } else {
                    kafkaTemplate.send(defaultTopic, event.getAggregateId(), event.getPayload());
                    log.debug("Published outbox event id={} type={} to topic={}",
                            event.getId(), event.getEventType(), defaultTopic);
                }
                event.setPublishedAt(Instant.now());
                outboxRepo.save(event);
            } catch (Exception e) {
                log.error("Failed to publish outbox event id={}: {}", event.getId(), e.getMessage());
                break; // Stop processing to maintain ordering
            }
        }
    }
}
