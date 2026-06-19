package zw.gov.mohcc.impilo.vashandi.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;
import zw.gov.mohcc.impilo.sharedkernel.events.DualEmitPolicy;
import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@Profile("!test")
@ConditionalOnProperty(name = "impilo.vashandi.kafka-events-enabled", havingValue = "true")
public class VashandiOutboxPublisher extends CompanionOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(VashandiOutboxPublisher.class);
    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public VashandiOutboxPublisher(EventOutboxRepository outboxRepository,
                                   KafkaTemplate<String, String> kafkaTemplate,
                                   @Value("${vashandi.v11.emit-mode:#{null}}") String emitMode) {
        super(new DualEmitPolicy(emitMode), new EventTopicRegistry("vashandi"));
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${vashandi.outbox.poll-interval-ms:2000}")
    @Transactional
    public void poll() {
        int n = publishPendingEvents();
        if (n > 0) {
            log.info("Published {} Vashandi outbox events", n);
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
        outboxRepository.findById(row.id()).ifPresent(e -> {
            e.setPublishedAt(publishedAt);
            outboxRepository.save(e);
        });
    }

    @Override
    protected void markFailed(OutboxRow row, String errorMessage) {
        outboxRepository.findById(row.id()).ifPresent(e -> {
            e.setPublishError(errorMessage);
            e.setRetryCount(e.getRetryCount() + 1);
            outboxRepository.save(e);
        });
    }

    @Override
    protected String resolveLegacyTopic(OutboxRow row) {
        return switch (row.aggregateType()) {
            case "WORKFORCE_PROFILE" -> "vashandi.workforce_profile.events";
            case "WORKFORCE_ASSIGNMENT" -> "vashandi.assignment.events";
            case "ROSTER" -> "vashandi.roster.events";
            case "SHIFT" -> "vashandi.shift.events";
            case "ATTENDANCE" -> "vashandi.attendance.events";
            case "LEAVE" -> "vashandi.leave.events";
            case "ACCESS_RISK" -> "vashandi.access_risk.events";
            default -> "vashandi.events";
        };
    }
}
