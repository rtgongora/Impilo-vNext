package zw.gov.mohcc.impilo.pharmacy.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.sharedkernel.events.DualEmitPolicy;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private static final int BATCH_SIZE = 100;

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final DualEmitPolicy emitPolicy;

    public OutboxPublisher(EventOutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           @Value("${pharmacy.v11.emit-mode:#{null}}") String ymlEmitMode) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.emitPolicy = new DualEmitPolicy(ymlEmitMode);
    }

    @Scheduled(fixedDelayString = "${pharmacy.outbox.poll-interval-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        List<EventOutboxEntity> pending =
                outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        if (pending.isEmpty()) {
            return;
        }

        List<EventOutboxEntity> batch = pending.size() > BATCH_SIZE
                ? pending.subList(0, BATCH_SIZE)
                : pending;

        int successCount = 0;
        for (EventOutboxEntity event : batch) {
            try {
                String key = event.getAggregateId();
                String payload = event.getPayload();

                if (emitPolicy.emitV11()) {
                    kafkaTemplate.send(routeTopic(event.getEventType()), key, payload);
                }
                if (emitPolicy.emitLegacy()) {
                    kafkaTemplate.send(routeLegacyTopic(event.getEventType()), key, payload);
                }

                event.setPublishedAt(OffsetDateTime.now());
                outboxRepository.save(event);
                successCount++;

            } catch (Exception e) {
                log.error("Failed to publish outbox event {} (type={}) to Kafka: {}",
                        event.getId(), event.getEventType(), e.getMessage(), e);
            }
        }

        if (successCount > 0) {
            log.info("Published {}/{} pharmacy outbox events to Kafka", successCount, batch.size());
        }
    }

    static String routeTopic(String eventType) {
        if (eventType == null) {
            return "clinical.pharmacy.events";
        }

        return switch (eventType) {
            case "DISPENSE_ORDER_RECEIVED",
                 "DISPENSE_ACCEPTED",
                 "DISPENSE_PICKING" -> "clinical.pharmacy.dispense.accepted";

            case "DISPENSE_PARTIAL" -> "clinical.pharmacy.dispense.partial";

            case "DISPENSE_COMPLETED" -> "clinical.pharmacy.dispense.completed";

            case "DISPENSE_BACKORDERED" -> "clinical.pharmacy.dispense.backordered";

            case "DISPENSE_CANCELLED" -> "clinical.pharmacy.dispense.cancelled";

            case "STOCK_MOVEMENT_RECORDED",
                 "STOCK_REVERSAL_RECORDED",
                 "RETURN_RECORDED",
                 "WASTAGE_RECORDED" -> "clinical.pharmacy.stock.movement_recorded";

            case "REVERSAL_COMPLETED" -> "clinical.pharmacy.reversal.completed";

            case "PICKUP_CLAIMED" -> "clinical.pharmacy.pickup.claimed";

            case "MUSHEX_CHARGE_REQUESTED" -> "clinical.pharmacy.mushex.charge";

            case "RECONCILE_RESOLVED" -> "clinical.pharmacy.reconcile.updated";

            default -> "clinical.pharmacy.events";
        };
    }

    static String routeLegacyTopic(String eventType) {
        if (eventType == null) {
            return "pharmacy.events";
        }

        return switch (eventType) {
            case "DISPENSE_ORDER_RECEIVED",
                 "DISPENSE_ACCEPTED",
                 "DISPENSE_PICKING" -> "pharmacy.dispense.accepted";

            case "DISPENSE_PARTIAL" -> "pharmacy.dispense.partial";

            case "DISPENSE_COMPLETED" -> "pharmacy.dispense.complete";

            case "DISPENSE_BACKORDERED" -> "pharmacy.dispense.backordered";

            case "DISPENSE_CANCELLED" -> "pharmacy.dispense.cancelled";

            case "STOCK_MOVEMENT_RECORDED",
                 "STOCK_REVERSAL_RECORDED",
                 "RETURN_RECORDED",
                 "WASTAGE_RECORDED" -> "pharmacy.stock.movement.recorded";

            case "REVERSAL_COMPLETED" -> "pharmacy.reversal.completed";

            case "PICKUP_CLAIMED" -> "pharmacy.pickup.claimed";

            case "MUSHEX_CHARGE_REQUESTED" -> "pharmacy.mushex.charge";

            case "RECONCILE_RESOLVED" -> "pharmacy.reconcile.updated";

            default -> "pharmacy.events";
        };
    }
}
