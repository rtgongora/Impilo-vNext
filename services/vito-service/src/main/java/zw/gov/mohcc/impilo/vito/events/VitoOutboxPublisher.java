package zw.gov.mohcc.impilo.vito.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import java.util.List;
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
        try {
            List<OutboxRow> events = (List<OutboxRow>) (List<?>) fetchUnpublished();
            if (events.isEmpty()) return;

            int published = 0;
            for (OutboxRow row : events) {
                try {
                    String legacyTopic = VitoEventMapper.resolveLegacyTopic(row.aggregateType());
                    sendToKafka(legacyTopic, row.aggregateId(), row.payloadJson());
                    markPublished(row, java.time.OffsetDateTime.now());
                    log.debug("VitoOutboxPublisher: published row id={} to topic={}", row.id(), legacyTopic);
                    published++;
                } catch (Exception e) {
                    log.error("VitoOutboxPublisher: failed row id={} type={}: {}", row.id(), row.eventType(), e.getMessage(), e);
                    break;
                }
            }
            if (published > 0) {
                log.info("VitoOutboxPublisher: published {} outbox events", published);
            }
        } catch (Exception e) {
            log.error("VitoOutboxPublisher.poll: error: {}", e.getMessage(), e);
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
