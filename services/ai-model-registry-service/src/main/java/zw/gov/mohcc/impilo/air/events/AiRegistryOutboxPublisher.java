package zw.gov.mohcc.impilo.air.events;

import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.air.persistence.entity.AiEventOutboxEntity;
import zw.gov.mohcc.impilo.air.persistence.repository.AiEventOutboxRepository;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;
import zw.gov.mohcc.impilo.sharedkernel.events.DualEmitPolicy;
import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;

@Component
@Profile("!test")
public class AiRegistryOutboxPublisher extends CompanionOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(AiRegistryOutboxPublisher.class);

    private final AiEventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public AiRegistryOutboxPublisher(
            AiEventOutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${air.v11.emit-mode:#{null}}") String ymlEmitMode) {
        super(new DualEmitPolicy(ymlEmitMode), new EventTopicRegistry("ai-registry"));
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        log.info("AiRegistryOutboxPublisher initialized emit-mode={}", effectiveEmitMode());
    }

    @Scheduled(fixedDelayString = "${air.outbox.poll-interval-ms:2000}")
    @Transactional
    public void poll() {
        int n = publishPendingEvents();
        if (n > 0) {
            log.debug("Published {} ai-registry outbox events", n);
        }
    }

    @Override
    protected List<? extends OutboxRow> fetchUnpublished() {
        return outboxRepository.findUnpublished(PageRequest.of(0, 100)).stream()
                .map(AiEventOutboxEntity::toOutboxRow)
                .toList();
    }

    @Override
    protected void sendToKafka(String topic, String key, String value) {
        kafkaTemplate.send(topic, key, value);
    }

    @Override
    protected void markPublished(OutboxRow row, OffsetDateTime publishedAt) {
        outboxRepository.findById(row.id()).ifPresent(entity -> {
            entity.setPublishedAt(publishedAt != null ? publishedAt : OffsetDateTime.now());
            entity.setPublishError(null);
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
        return "ai-registry.events";
    }
}
