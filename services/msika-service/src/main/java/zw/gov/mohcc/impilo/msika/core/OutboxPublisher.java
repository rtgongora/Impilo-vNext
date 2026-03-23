package zw.gov.mohcc.impilo.msika.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;
import zw.gov.mohcc.impilo.sharedkernel.events.DualEmitPolicy;
import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;
import zw.gov.mohcc.impilo.msika.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.msika.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * MSIKA outbox publisher using shared CompanionOutboxPublisher base.
 * Partition key defaults to aggregate_id (catalog or item ID).
 */
@Service
public class OutboxPublisher extends CompanionOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(EventOutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           @Value("${msika.v11.emit-mode:#{null}}") String ymlEmitMode) {
        super(new DualEmitPolicy(ymlEmitMode), new EventTopicRegistry("msika"));
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        log.info("MsikaOutboxPublisher initialized with emit-mode={}", effectiveEmitMode());
    }

    public OutboxPublisher(EventOutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate) {
        this(outboxRepository, kafkaTemplate, null);
    }

    public String resolveTopic(String eventType) {
        if (eventType == null) return "msika.core.events";
        return switch (eventType) {
            case "CATALOG_PUBLISHED", "CATALOG_APPROVED" -> "msika.core.catalog.published";
            case "ITEM_CREATED", "ITEM_UPDATED", "ITEM_DELETED" -> "msika.core.item.changed";
            case "MAPPING_APPROVED" -> "msika.core.mapping.approved";
            default -> "msika.core.events";
        };
    }

    @Scheduled(fixedDelayString = "${msika.outbox.poll-interval-ms:2000}")
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
        if (row.eventType() == null) return "msika.core.events";
        return switch (row.eventType()) {
            case "CATALOG_PUBLISHED", "CATALOG_APPROVED" -> "msika.core.catalog.published";
            case "ITEM_CREATED", "ITEM_UPDATED", "ITEM_DELETED" -> "msika.core.item.changed";
            case "MAPPING_APPROVED" -> "msika.core.mapping.approved";
            default -> "msika.core.events";
        };
    }
}
