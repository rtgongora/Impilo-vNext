package zw.gov.mohcc.impilo.butano.events;

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
import zw.gov.mohcc.impilo.butano.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.butano.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * BUTANO outbox publisher using shared CompanionOutboxPublisher base.
 *
 * <p>Replaces the legacy OutboxPublisher with dual-emit support
 * (legacy topics + v1.1 EventEnvelope). Supports DUAL / LEGACY_ONLY /
 * V1_1_ONLY emit modes via {@code butano.v11.emit-mode} config or
 * {@code EMIT_MODE} environment variable.</p>
 *
 * <p>Legacy topics:</p>
 * <ul>
 *   <li>{@code butano.resource.created} — new FHIR resources stored in the SHR</li>
 *   <li>{@code butano.resource.updated} — existing FHIR resources modified</li>
 *   <li>{@code butano.reconcile.completed} — O-CPID to CPID reconciliation finished</li>
 * </ul>
 */
@Component
public class ButanoOutboxPublisher extends CompanionOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(ButanoOutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public ButanoOutboxPublisher(EventOutboxRepository outboxRepository,
                                  KafkaTemplate<String, String> kafkaTemplate,
                                  @Value("${butano.v11.emit-mode:#{null}}") String ymlEmitMode) {
        super(new DualEmitPolicy(ymlEmitMode), new EventTopicRegistry("butano"));
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        log.info("ButanoOutboxPublisher initialized with emit-mode={}", effectiveEmitMode());
    }

    @Scheduled(fixedDelayString = "${butano.outbox.poll-interval-ms:2000}")
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
            entity.setRetryCount(row.retryCount() + 1);
            outboxRepository.save(entity);
        });
    }

    @Override
    protected String resolveLegacyTopic(OutboxRow row) {
        if (row.eventType() == null) {
            return "butano.events";
        }
        return switch (row.eventType()) {
            case "RESOURCE_CREATED" -> "butano.resource.created";
            case "RESOURCE_UPDATED" -> "butano.resource.updated";
            case "RECONCILE_COMPLETED" -> "butano.reconcile.completed";
            default -> "butano.events";
        };
    }
}
