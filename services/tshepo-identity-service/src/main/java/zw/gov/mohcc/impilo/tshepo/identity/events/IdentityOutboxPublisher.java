package zw.gov.mohcc.impilo.tshepo.identity.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.repository.EventOutboxRepository;

import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;
import zw.gov.mohcc.impilo.sharedkernel.events.DualEmitPolicy;
import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Polls the event_outbox table and publishes unpublished events to Kafka.
 *
 * <p>Uses the transactional outbox pattern for reliable, at-least-once event
 * delivery. Events are ordered by creation time and published sequentially.
 * If any event fails to publish, processing stops to maintain ordering.</p>
 */
@Component
public class IdentityOutboxPublisher extends CompanionOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(IdentityOutboxPublisher.class);

    private final EventOutboxRepository outboxRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public IdentityOutboxPublisher(EventOutboxRepository outboxRepo,
                                    KafkaTemplate<String, String> kafkaTemplate,
                                    @Value("${tshepo.outbox.kafka-topic:platform.identity.events}") String topic,
                                    @Value("${tshepo.identity.v11.emit-mode:#{null}}") String ymlEmitMode) {
        super(new DualEmitPolicy(ymlEmitMode), new EventTopicRegistry("identity"));
        this.outboxRepo = outboxRepo;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Scheduled(fixedDelayString = "${tshepo.outbox.poll-interval-ms:1000}")
    @Transactional
    public void poll() {
        int published = publishPendingEvents();
        if (published > 0) {
            log.debug("Published {} identity outbox events", published);
        }
    }

    @Override
    protected List<? extends OutboxRow> fetchUnpublished() {
        return outboxRepo.findUnpublished().stream()
                .map(EventOutboxEntity::toOutboxRow)
                .toList();
    }

    @Override
    protected void sendToKafka(String topic, String key, String value) {
        kafkaTemplate.send(topic, key, value);
    }

    @Override
    protected void markPublished(OutboxRow row, OffsetDateTime publishedAt) {
        outboxRepo.findById(row.id()).ifPresent(entity -> {
            entity.setPublishedAt(publishedAt != null ? publishedAt.toInstant() : Instant.now());
            outboxRepo.save(entity);
        });
    }

    @Override
    protected void markFailed(OutboxRow row, String errorMessage) {
        outboxRepo.findById(row.id()).ifPresent(entity -> {
            entity.setPublishError(errorMessage);
            entity.setRetryCount(row.retryCount() + 1);
            outboxRepo.save(entity);
        });
    }

    /** Identity has always published every event to one configured topic. */
    @Override
    protected String resolveLegacyTopic(OutboxRow row) {
        return topic;
    }
}
