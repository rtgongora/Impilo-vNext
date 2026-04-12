package zw.gov.mohcc.impilo.surv.events;

import java.time.OffsetDateTime;
import java.util.List;
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
import zw.gov.mohcc.impilo.surv.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.surv.persistence.repository.EventOutboxRepository;

/**
 * Publishes surveillance outbox rows to the analytics bus topics using
 * {@link CompanionOutboxPublisher} (legacy emit to {@code analytics.*}).
 */
@Component
public class SurvOutboxPublisher extends CompanionOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(SurvOutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public SurvOutboxPublisher(EventOutboxRepository outboxRepository,
                             KafkaTemplate<String, String> kafkaTemplate,
                             @Value("${surv.v11.emit-mode:#{null}}") String ymlEmitMode) {
        super(new DualEmitPolicy(ymlEmitMode), new EventTopicRegistry("surveillance"));
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        log.info("SurvOutboxPublisher initialized with effective emit-mode={}", effectiveEmitMode());
    }

    @Scheduled(fixedDelayString = "${surv.outbox.poll-interval-ms:2000}")
    @Transactional
    public void poll() {
        int count = publishPendingEvents();
        if (count > 0) {
            log.info("Published {} surveillance outbox events", count);
        }
    }

    @Override
    protected List<? extends OutboxRow> fetchUnpublished() {
        return outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc().stream()
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
            outboxRepository.save(entity);
        });
    }

    @Override
    protected String resolveLegacyTopic(OutboxRow row) {
        return resolveAnalyticsTopic(row.eventType());
    }

    /**
     * Maps internal outbox event types to Kafka bus topics (analytics channel).
     */
    static String resolveAnalyticsTopic(String eventType) {
        if (eventType == null) {
            return "analytics.surveillance.event";
        }
        return switch (eventType) {
            case "ALERT_TRIGGERED" -> "analytics.surveillance.alert";
            case "SIGNAL_CREATED", "SIGNAL_HIT", "CASE_OPENED" -> "analytics.surveillance.event";
            default -> "analytics.surveillance.event";
        };
    }
}
