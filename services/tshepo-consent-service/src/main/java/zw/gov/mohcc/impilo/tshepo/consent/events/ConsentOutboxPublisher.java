package zw.gov.mohcc.impilo.tshepo.consent.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;
import zw.gov.mohcc.impilo.sharedkernel.events.DualEmitPolicy;
import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;
import zw.gov.mohcc.impilo.tshepo.consent.config.ConsentProperties;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.EventOutboxEntity;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.EventOutboxRepository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Publishes consent outbox rows via dual emit.
 *
 * <p>The configured legacy topic ({@code platform.consent.events} by default) keeps carrying
 * the raw domain payload, which is what {@code ConsentRevocationConsumer} in fhir-gateway
 * reads. The v1.1 envelope goes to {@code impilo.consent.*}, a namespace that cannot collide
 * with the configured legacy topic, so the existing consumer is unaffected.</p>
 */
@Component
public class ConsentOutboxPublisher extends CompanionOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(ConsentOutboxPublisher.class);

    private final EventOutboxRepository outboxRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ConsentProperties properties;

    public ConsentOutboxPublisher(EventOutboxRepository outboxRepo,
                                  KafkaTemplate<String, String> kafkaTemplate,
                                  ConsentProperties properties,
                                  @Value("${consent.v11.emit-mode:#{null}}") String ymlEmitMode) {
        super(new DualEmitPolicy(ymlEmitMode), new EventTopicRegistry("consent"));
        this.outboxRepo = outboxRepo;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        log.info("ConsentOutboxPublisher initialised with emit-mode={}", effectiveEmitMode());
    }

    @Scheduled(fixedDelayString = "${consent.outbox.poll-interval-ms:1000}")
    @Transactional
    public void poll() {
        int published = publishPendingEvents();
        if (published > 0) {
            log.debug("Published {} consent outbox events", published);
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

    /** Consent has always published every event to one configured topic. */
    @Override
    protected String resolveLegacyTopic(OutboxRow row) {
        return properties.getOutbox().getKafkaTopic();
    }
}
