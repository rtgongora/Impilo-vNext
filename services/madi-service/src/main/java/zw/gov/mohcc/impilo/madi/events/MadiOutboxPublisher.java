package zw.gov.mohcc.impilo.madi.events;

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
import zw.gov.mohcc.impilo.madi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.madi.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@Profile("!test")
public class MadiOutboxPublisher extends CompanionOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(MadiOutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public MadiOutboxPublisher(EventOutboxRepository outboxRepository,
                                KafkaTemplate<String, String> kafkaTemplate,
                                @Value("${madi.v11.emit-mode:#{null}}") String ymlEmitMode) {
        super(new DualEmitPolicy(ymlEmitMode), new EventTopicRegistry("madi"));
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        log.info("MadiOutboxPublisher initialized with emit-mode={}", effectiveEmitMode());
    }

    @Scheduled(fixedDelayString = "${madi.outbox.poll-interval-ms:2000}")
    @Transactional
    public void poll() {
        int count = publishPendingEvents();
        if (count > 0) {
            log.info("Published {} SIMBA outbox events", count);
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
            // An emergency action allowed without a validated grant. Routed straight at the audit
            // plane rather than a madi topic: this is not blood-bank domain traffic, it is the
            // record of a control that did not run, and it needs to reach the hash chain that
            // clinical governance actually reads. The payload is shaped as tshepo-audit's
            // AuditEventRequest so AuditKafkaConsumer can append it without a translation step.
            // See MadiUngovernedOverrideRecorder for what is guaranteed (the local row) versus
            // best-effort (arrival in the chain).
            case "BREAK_GLASS_OVERRIDE" -> "tshepo.audit.events";
            case "DONOR" -> "madi.donor";
            case "DONATION_DRIVE" -> "madi.donation.drive";
            case "BLOOD_UNIT" -> "madi.blood.unit";
            case "BLOOD_ORDER" -> "madi.blood.order";
            case "TRANSFUSION" -> "madi.transfusion";
            case "HAEMOVIGILANCE" -> "madi.haemovigilance";
            default -> "madi.events";
        };
    }
}
