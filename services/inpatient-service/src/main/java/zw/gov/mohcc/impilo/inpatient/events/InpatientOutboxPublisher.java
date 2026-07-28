package zw.gov.mohcc.impilo.inpatient.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;
import zw.gov.mohcc.impilo.sharedkernel.events.DualEmitPolicy;
import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Polls the inpatient event outbox and publishes to Kafka (legacy + v1.1 envelope)
 * using {@link CompanionOutboxPublisher}.
 */
@Component
@Profile("!test")
public class InpatientOutboxPublisher extends CompanionOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(InpatientOutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public InpatientOutboxPublisher(EventOutboxRepository outboxRepository,
                                  KafkaTemplate<String, String> kafkaTemplate,
                                  @Value("${inpatient.v11.emit-mode:#{null}}") String ymlEmitMode) {
        super(new DualEmitPolicy(ymlEmitMode), new EventTopicRegistry("inpatient"));
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        log.info("InpatientOutboxPublisher initialized with effective emit-mode={}", effectiveEmitMode());
    }

    @Scheduled(fixedDelayString = "${inpatient.outbox.poll-interval-ms:2000}")
    @Transactional
    public void poll() {
        int count = publishPendingEvents();
        if (count > 0) {
            log.debug("Published {} inpatient outbox events", count);
        }
    }

    @Override
    protected List<? extends OutboxRow> fetchUnpublished() {
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
        return switch (row.aggregateType()) {
            case "TRANSFER" -> "inpatient.transfer";
            case "ADMISSION" -> "inpatient.admission";
            case "BED" -> "inpatient.bed";
            case "ESCALATION" -> "inpatient.ews";
            case "SAFETY" -> "inpatient.safety";
            case "MEDICATION" -> "inpatient.medication";
            case "DISCHARGE_SUMMARY" -> "inpatient.discharge";
            case "EMERGENCY_ACTIVATION" -> "inpatient.emergency";
            default -> "inpatient.events";
        };
    }
}
