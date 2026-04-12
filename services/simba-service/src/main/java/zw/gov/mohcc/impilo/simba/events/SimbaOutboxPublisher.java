package zw.gov.mohcc.impilo.simba.events;

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
import zw.gov.mohcc.impilo.simba.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@Profile("!test")
public class SimbaOutboxPublisher extends CompanionOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(SimbaOutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public SimbaOutboxPublisher(EventOutboxRepository outboxRepository,
                                KafkaTemplate<String, String> kafkaTemplate,
                                @Value("${simba.v11.emit-mode:#{null}}") String ymlEmitMode) {
        super(new DualEmitPolicy(ymlEmitMode), new EventTopicRegistry("simba"));
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        log.info("SimbaOutboxPublisher initialized with emit-mode={}", effectiveEmitMode());
    }

    @Scheduled(fixedDelayString = "${simba.outbox.poll-interval-ms:2000}")
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
            case "WELLNESS_CONNECT" -> "simba.wellness.connect";
            case "WELLNESS_PROFILE" -> "simba.wellness.profile";
            default -> "simba.wellness";
        };
    }
}
