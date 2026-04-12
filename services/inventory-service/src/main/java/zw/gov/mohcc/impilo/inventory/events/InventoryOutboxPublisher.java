package zw.gov.mohcc.impilo.inventory.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;
import zw.gov.mohcc.impilo.sharedkernel.events.DualEmitPolicy;
import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;
import zw.gov.mohcc.impilo.inventory.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Publishes {@code inv_event_outbox} rows to Kafka using {@link CompanionOutboxPublisher}
 * (legacy topic routing + optional v1.1 EventEnvelope dual emit).
 */
@Component
@Profile("!test")
public class InventoryOutboxPublisher extends CompanionOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(InventoryOutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public InventoryOutboxPublisher(EventOutboxRepository outboxRepository,
                                    KafkaTemplate<String, String> kafkaTemplate,
                                    @Value("${inventory.v11.emit-mode:#{null}}") String ymlEmitMode) {
        super(new DualEmitPolicy(ymlEmitMode), new EventTopicRegistry("inventory"));
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        log.info("InventoryOutboxPublisher initialized emit-mode={}", effectiveEmitMode());
    }

    @Scheduled(fixedDelayString = "${inventory.outbox.poll-interval-ms:2000}")
    @Transactional
    public void poll() {
        int n = publishPendingEvents();
        if (n > 0) {
            log.info("Published {} inventory outbox events", n);
        }
    }

    @Override
    protected List<? extends OutboxRow> fetchUnpublished() {
        return outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc().stream()
                .map(e -> e.toOutboxRow())
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
            entity.setRetryCount(entity.getRetryCount() != null ? entity.getRetryCount() + 1 : 1);
            outboxRepository.save(entity);
        });
    }

    @Override
    protected String resolveLegacyTopic(OutboxRow row) {
        return routeTopic(row.eventType());
    }

    static String routeTopic(String eventType) {
        if (eventType == null) {
            return "inventory.events";
        }
        return switch (eventType) {
            case "LEDGER_EVENT_CREATED" -> "inventory.ledger.event.created";
            case "ONHAND_UPDATED" -> "inventory.onhand.updated";
            case "STOCKOUT_RISK" -> "inventory.stockout.risk";
            case "COUNT_COMPLETED" -> "inventory.count.completed";
            case "REQUISITION_CREATED" -> "inventory.requisition.created";
            case "REQUISITION_FULFILLED" -> "inventory.requisition.fulfilled";
            case "MUSHEX_CHARGE_REQUESTED" -> "inventory.mushex.charge";
            case "RECONCILE_RESOLVED" -> "inventory.reconcile.resolved";
            default -> "inventory.events";
        };
    }
}
