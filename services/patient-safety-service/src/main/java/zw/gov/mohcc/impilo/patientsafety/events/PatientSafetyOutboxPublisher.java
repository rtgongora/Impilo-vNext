package zw.gov.mohcc.impilo.patientsafety.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.patientsafety.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.patientsafety.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;
import zw.gov.mohcc.impilo.sharedkernel.events.DualEmitPolicy;
import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Polls {@code ps_event_outbox} and publishes to Kafka using dual-emit (legacy topics + v1.1
 * envelopes). Disabled under the {@code test} profile and when the publisher flag is off.
 */
@Component
@Profile("!test")
@ConditionalOnProperty(name = "patientsafety.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class PatientSafetyOutboxPublisher extends CompanionOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(PatientSafetyOutboxPublisher.class);

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public PatientSafetyOutboxPublisher(OutboxEventRepository outboxRepository,
                                        KafkaTemplate<String, String> kafkaTemplate,
                                        @Value("${patientsafety.v11.emit-mode:#{null}}") String ymlEmitMode) {
        super(new DualEmitPolicy(ymlEmitMode), new EventTopicRegistry("patientsafety"));
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        log.info("PatientSafetyOutboxPublisher initialized with emit-mode={}", effectiveEmitMode());
    }

    @Scheduled(fixedDelayString = "${patientsafety.outbox.poll-interval-ms:2000}")
    @Transactional
    public void poll() {
        int count = publishPendingEvents();
        if (count > 0) {
            log.debug("Published {} patient-safety outbox events", count);
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
            return "patientsafety.events";
        }
        return switch (row.aggregateType()) {
            case "SAFETY_REPORT" -> "patientsafety.reports";
            case "SAFETY_CASE" -> "patientsafety.cases";
            case "AEFI_INVESTIGATION" -> "patientsafety.investigations";
            default -> "patientsafety.events";
        };
    }
}
