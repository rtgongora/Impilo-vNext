package zw.gov.mohcc.impilo.coverage.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.coverage.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.coverage.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;
import zw.gov.mohcc.impilo.sharedkernel.events.DualEmitPolicy;
import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Polls {@code cv_event_outbox} and publishes to Kafka using dual-emit (legacy topics + v1.1 envelopes).
 */
@Component
@Profile("!test")
public class CoverageOutboxPublisher extends CompanionOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(CoverageOutboxPublisher.class);

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public CoverageOutboxPublisher(OutboxEventRepository outboxRepository,
                                   KafkaTemplate<String, String> kafkaTemplate,
                                   @Value("${coverage.v11.emit-mode:#{null}}") String ymlEmitMode) {
        super(new DualEmitPolicy(ymlEmitMode), new EventTopicRegistry("coverage"));
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        log.info("CoverageOutboxPublisher initialized with emit-mode={}", effectiveEmitMode());
    }

    @Scheduled(fixedDelayString = "${coverage.outbox.poll-interval-ms:2000}")
    @Transactional
    public void poll() {
        int count = publishPendingEvents();
        if (count > 0) {
            log.debug("Published {} coverage outbox events", count);
        }
    }

    @Override
    protected List<? extends OutboxRow> fetchUnpublished() {
        return outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()
                .stream()
                .map(OutboxEventEntity::toOutboxRow)
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
        if (row.aggregateType() == null) {
            return "coverage.events";
        }
        return switch (row.aggregateType()) {
            case "COVERAGE_PLAN" -> "coverage.plans";
            case "MEMBER_COVERAGE" -> "coverage.membership";
            case "ELIGIBILITY" -> "coverage.eligibility";
            case "PREAUTH" -> "coverage.preauth";
            case "CLAIM" -> "coverage.claims";
            case "CONTRIBUTION" -> "coverage.contributions";
            case "REMITTANCE" -> "coverage.remittance";
            case "APPEAL" -> "coverage.appeals";
            case "PROVIDER_CONTRACT", "PROVIDER_NETWORK", "NETWORK_MEMBER" -> "coverage.events";
            default -> "coverage.events";
        };
    }
}
