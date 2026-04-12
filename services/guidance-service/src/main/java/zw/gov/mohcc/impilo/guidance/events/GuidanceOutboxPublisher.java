package zw.gov.mohcc.impilo.guidance.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.guidance.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.guidance.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;
import zw.gov.mohcc.impilo.sharedkernel.events.DualEmitPolicy;
import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class GuidanceOutboxPublisher extends CompanionOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(GuidanceOutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public GuidanceOutboxPublisher(
            EventOutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${guidance.v11.emit-mode:#{null}}") String ymlEmitMode) {
        super(new DualEmitPolicy(ymlEmitMode), new EventTopicRegistry("guidance"));
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        log.info("GuidanceOutboxPublisher initialized with effective emit-mode={}", effectiveEmitMode());
    }

    @Scheduled(fixedDelayString = "${guidance.outbox.poll-interval-ms:2000}")
    @Transactional
    public void poll() {
        int count = publishPendingEvents();
        if (count > 0) {
            log.debug("Published {} guidance outbox events", count);
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
            int prior = entity.getRetryCount() != null ? entity.getRetryCount() : 0;
            entity.setRetryCount(prior + 1);
            outboxRepository.save(entity);
        });
    }

    @Override
    protected String resolveLegacyTopic(OutboxRow row) {
        return "guidance.events";
    }
}
