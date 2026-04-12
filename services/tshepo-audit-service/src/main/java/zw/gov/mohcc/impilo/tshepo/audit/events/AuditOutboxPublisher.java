package zw.gov.mohcc.impilo.tshepo.audit.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;
import zw.gov.mohcc.impilo.sharedkernel.events.DualEmitPolicy;
import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.repository.EventOutboxRepository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Polls {@code tshepo_audit.event_outbox} and publishes via {@link CompanionOutboxPublisher}.
 */
@Component
public class AuditOutboxPublisher extends CompanionOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuditOutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public AuditOutboxPublisher(
            @Value("${tshepo-audit.v11.emit-mode:#{null}}") String emitModeOverride,
            EventOutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate) {
        super(new DualEmitPolicy(emitModeOverride), new EventTopicRegistry("tshepo-audit"));
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        log.info("AuditOutboxPublisher initialized with emit-mode={}", effectiveEmitMode());
    }

    @Scheduled(fixedDelayString = "${tshepo-audit.outbox.poll-interval-ms:2000}")
    @Transactional
    public void poll() {
        publishPendingEvents();
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
            entity.setPublishedAt(publishedAt != null ? publishedAt.toInstant() : Instant.now());
            outboxRepository.save(entity);
        });
    }

    @Override
    protected void markFailed(OutboxRow row, String errorMessage) {
        outboxRepository.findById(row.id()).ifPresent(entity -> {
            entity.setPublishError(errorMessage);
            int prior = entity.getRetryCount() != null ? entity.getRetryCount() : 0;
            entity.setRetryCount(prior + 1);
            outboxRepository.save(entity);
        });
    }

    @Override
    protected String resolveLegacyTopic(OutboxRow row) {
        return null;
    }
}
