package zw.gov.mohcc.impilo.rito.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.rito.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.rito.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;
import zw.gov.mohcc.impilo.sharedkernel.events.DualEmitPolicy;
import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Relays unpublished Rito outbox rows to Kafka. Disabled under the {@code test} profile
 * so unit tests never need a broker.
 */
@Component
@Profile("!test")
public class RitoOutboxPublisher extends CompanionOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(RitoOutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public RitoOutboxPublisher(EventOutboxRepository outboxRepository,
                               KafkaTemplate<String, String> kafkaTemplate,
                               @Value("${rito.emit-mode:#{null}}") String ymlEmitMode) {
        super(new DualEmitPolicy(ymlEmitMode), new EventTopicRegistry("rito"));
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        log.info("RitoOutboxPublisher initialized with emit-mode={}", effectiveEmitMode());
    }

    @Scheduled(fixedDelayString = "${rito.outbox.poll-interval-ms:2000}")
    @Transactional
    public void poll() {
        int count = publishPendingEvents();
        if (count > 0) {
            log.info("Published {} Rito outbox events", count);
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
        return switch (row.aggregateType()) {
            case "CASE" -> "rito.case";
            case "SAFETY_INCIDENT" -> "rito.safety";
            case "QUALITY_SIGNAL" -> "rito.quality.signal";
            case "AUDIT" -> "rito.audit";
            case "CORRECTIVE_ACTION" -> "rito.corrective_action";
            case "QI_PLAN" -> "rito.qi_plan";
            case "SURVEY" -> "rito.survey";
            case "PROVIDER_RATING" -> "rito.rating";
            case "REPUTATION" -> "rito.reputation";
            default -> "rito.events";
        };
    }
}
