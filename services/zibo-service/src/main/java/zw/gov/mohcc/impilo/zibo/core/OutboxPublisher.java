package zw.gov.mohcc.impilo.zibo.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;
import zw.gov.mohcc.impilo.sharedkernel.events.DualEmitPolicy;
import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;
import zw.gov.mohcc.impilo.zibo.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.zibo.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * ZIBO outbox publisher using shared CompanionOutboxPublisher base.
 * Supports DUAL / LEGACY_ONLY / V1_1_ONLY emit modes.
 */
@Service
public class OutboxPublisher extends CompanionOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(EventOutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           @Value("${zibo.v11.emit-mode:#{null}}") String ymlEmitMode) {
        super(new DualEmitPolicy(ymlEmitMode), new EventTopicRegistry("zibo"));
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        log.info("ZiboOutboxPublisher initialized with emit-mode={}", effectiveEmitMode());
    }

    @Scheduled(fixedDelayString = "${zibo.outbox.poll-interval-ms:2000}")
    @Transactional
    public void poll() {
        int count = publishPendingEvents();
        if (count > 0) {
            log.info("Published {} outbox events", count);
        }
    }

    @Override
    protected List<OutboxRow> fetchUnpublished() {
        return outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()
                .stream()
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
        if (row.eventType() == null) return "zibo.events";
        return switch (row.eventType()) {
            case "ARTIFACT_CREATED", "ARTIFACT_UPDATED",
                 "ARTIFACT_PUBLISHED", "ARTIFACT_DEPRECATED", "ARTIFACT_RETIRED"
                    -> "zibo.artifact.lifecycle";
            case "PACK_PUBLISHED", "PACK_DEPRECATED"
                    -> "zibo.pack.lifecycle";
            case "PACK_ASSIGNED"
                    -> "zibo.pack.assigned";
            case "VALIDATION_FAILED"
                    -> "zibo.validation.failed";
            default -> "zibo.events";
        };
    }

    @Override
    protected String resolveV11Topic(OutboxRow row) {
        if (row.eventType() == null) return "kernel.zibo.events";
        return switch (row.eventType()) {
            case "ARTIFACT_CREATED", "ARTIFACT_UPDATED",
                 "ARTIFACT_PUBLISHED", "ARTIFACT_DEPRECATED", "ARTIFACT_RETIRED"
                    -> "kernel.zibo.artifact";
            case "PACK_PUBLISHED", "PACK_DEPRECATED", "PACK_ASSIGNED"
                    -> "kernel.zibo.pack";
            case "VALIDATION_FAILED"
                    -> "kernel.zibo.validation";
            default -> "kernel.zibo.events";
        };
    }
}
