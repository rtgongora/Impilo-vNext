package zw.gov.mohcc.impilo.vito.events;

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
import zw.gov.mohcc.impilo.vito.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * VITO Event Outbox Publisher — refactored to use shared CompanionOutboxPublisher.
 *
 * <h3>Emit mode precedence</h3>
 * <ol>
 *   <li>{@code EMIT_MODE} system property (highest priority)</li>
 *   <li>{@code EMIT_MODE} environment variable</li>
 *   <li>{@code vito.v11.emit-mode} from application.yml</li>
 *   <li>Default: DUAL</li>
 * </ol>
 */
@Component
public class VitoOutboxPublisher extends CompanionOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(VitoOutboxPublisher.class);
    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public VitoOutboxPublisher(EventOutboxRepository outboxRepository,
                                KafkaTemplate<String, String> kafkaTemplate,
                                @Value("${vito.v11.emit-mode:#{null}}") String ymlEmitMode) {
        super(new DualEmitPolicy(ymlEmitMode), new EventTopicRegistry("vito"));
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        log.info("VitoOutboxPublisher initialized with effective emit-mode={}", effectiveEmitMode());
    }

    @Scheduled(fixedDelayString = "${vito.outbox.poll-interval-ms:2000}")
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
            outboxRepository.save(entity);
        });
    }

    @Override
    protected String resolveLegacyTopic(OutboxRow row) {
        return VitoEventMapper.resolveLegacyTopic(row.aggregateType());
    }

    @Override
    protected String resolveV11Topic(OutboxRow row) {
        return VitoEventMapper.resolveV11Topic(row.aggregateType());
    }

}
