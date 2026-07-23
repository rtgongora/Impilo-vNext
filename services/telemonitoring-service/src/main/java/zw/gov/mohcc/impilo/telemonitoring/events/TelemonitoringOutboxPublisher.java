package zw.gov.mohcc.impilo.telemonitoring.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;
import zw.gov.mohcc.impilo.sharedkernel.events.DualEmitPolicy;
import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Relays unpublished telemonitoring outbox rows to Kafka. Disabled under the {@code test}
 * profile so unit tests never need a broker.
 *
 * <p>Legacy topic routing: plan lifecycle rows carry the topic-shaped event type
 * {@code telemonitoring.plan.{action}.v1} (the epic's published contract) and are
 * emitted on a topic of exactly that name; anything else lands on
 * {@code telemonitoring.events}.</p>
 */
@Component
@Profile("!test")
public class TelemonitoringOutboxPublisher extends CompanionOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(TelemonitoringOutboxPublisher.class);

    static final String PLAN_TOPIC_PREFIX = "telemonitoring.plan.";
    static final String DEFAULT_TOPIC = "telemonitoring.events";

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public TelemonitoringOutboxPublisher(EventOutboxRepository outboxRepository,
                                         KafkaTemplate<String, String> kafkaTemplate,
                                         @Value("${telemonitoring.emit-mode:#{null}}") String ymlEmitMode) {
        super(new DualEmitPolicy(ymlEmitMode), new EventTopicRegistry("telemonitoring"));
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        log.info("TelemonitoringOutboxPublisher initialized with emit-mode={}", effectiveEmitMode());
    }

    @Scheduled(fixedDelayString = "${telemonitoring.outbox.poll-interval-ms:2000}")
    @Transactional
    public void poll() {
        int count = publishPendingEvents();
        if (count > 0) {
            log.info("Published {} telemonitoring outbox events", count);
        }
    }

    @Override
    protected List<? extends OutboxRow> fetchUnpublished() {
        return outboxRepository.findUnpublished(PageRequest.of(0, 100)).stream()
                .map(EventOutboxEntity::toOutboxRow)
                .toList();
    }

    @Override
    protected void sendToKafka(String topic, String key, String value) {
        kafkaTemplate.send(topic, key, value);
    }

    @Override
    protected void markPublished(OutboxRow row, OffsetDateTime publishedAt) {
        outboxRepository.findById(row.id()).ifPresent(entity -> {
            entity.setPublishedAt(publishedAt);
            outboxRepository.save(entity);
        });
    }

    @Override
    protected void markFailed(OutboxRow row, String errorMessage) {
        outboxRepository.findById(row.id()).ifPresent(entity -> {
            entity.setPublishError(errorMessage);
            entity.setRetryCount(entity.getRetryCount() + 1);
            outboxRepository.save(entity);
        });
    }

    @Override
    protected String resolveLegacyTopic(OutboxRow row) {
        return resolveTopic(row.aggregateType(), row.eventType());
    }

    /**
     * Routing convention (unit-tested): plan lifecycle events publish on the topic named
     * by their own event type ({@code telemonitoring.plan.{action}.v1}); everything else
     * publishes on {@code telemonitoring.events}.
     */
    static String resolveTopic(String aggregateType, String eventType) {
        if (TelemonitoringEventEmitter.AGGREGATE_MONITORING_PLAN.equals(aggregateType)
                && eventType != null && eventType.startsWith(PLAN_TOPIC_PREFIX) && eventType.endsWith(".v1")) {
            return eventType;
        }
        return DEFAULT_TOPIC;
    }
}
