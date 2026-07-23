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

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Scheduled outbox publisher that polls for unpublished events and delivers them to Kafka.
 *
 * <p>Implements the transactional outbox pattern: domain events are written to the
 * {@code rx_event_outbox} table within the same database transaction as the domain
 * change, ensuring at-least-once delivery to Kafka without distributed transactions.</p>
 *
 * <h3>Topic Routing</h3>
 * <table>
 *   <tr><th>Event Type</th><th>Kafka Topic</th></tr>
 *   <tr><td>DISPENSE_ORDER_RECEIVED, DISPENSE_ACCEPTED, DISPENSE_PICKING</td><td>pharmacy.dispense.accepted</td></tr>
 *   <tr><td>DISPENSE_PARTIAL</td><td>pharmacy.dispense.partial</td></tr>
 *   <tr><td>DISPENSE_COMPLETED</td><td>pharmacy.dispense.complete</td></tr>
 *   <tr><td>DISPENSE_BACKORDERED</td><td>pharmacy.dispense.backordered</td></tr>
 *   <tr><td>DISPENSE_CANCELLED</td><td>pharmacy.dispense.cancelled</td></tr>
 *   <tr><td>STOCK_MOVEMENT_RECORDED, STOCK_REVERSAL_RECORDED</td><td>pharmacy.stock.movement.recorded</td></tr>
 *   <tr><td>RETURN_RECORDED, WASTAGE_RECORDED</td><td>pharmacy.stock.movement.recorded</td></tr>
 *   <tr><td>REVERSAL_COMPLETED</td><td>pharmacy.reversal.completed</td></tr>
 *   <tr><td>PICKUP_CLAIMED</td><td>pharmacy.pickup.claimed</td></tr>
 *   <tr><td>MUSHEX_CHARGE_REQUESTED</td><td>pharmacy.mushex.charge</td></tr>
 *   <tr><td>RECONCILE_RESOLVED</td><td>pharmacy.reconcile.updated</td></tr>
 *   <tr><td>default</td><td>pharmacy.events</td></tr>
 * </table>
 *
 * <p>The publisher runs on a fixed-delay schedule (default 2000ms, configurable via
 * {@code pharmacy.outbox.poll-interval-ms}) and processes events in batches of up to 100.</p>
 */
@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    /** Maximum number of events to process per poll cycle. */
    private static final int BATCH_SIZE = 100;

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final boolean dualEmitCoreTransactionEnabled;
    private final String coreTransactionTopic;

    /**
     * Constructs the OutboxPublisher with the outbox repository and Kafka template.
     *
     * @param outboxRepository repository for outbox event queries and updates
     * @param kafkaTemplate    Kafka template for publishing events
     */
    public OutboxPublisher(EventOutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           @Value("${pharmacy.outbox.dual-emit-core-transaction-enabled:true}") boolean dualEmitCoreTransactionEnabled,
                           @Value("${pharmacy.outbox.core-transaction-topic:core.transaction.events}") String coreTransactionTopic) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.dualEmitCoreTransactionEnabled = dualEmitCoreTransactionEnabled;
        this.coreTransactionTopic = coreTransactionTopic;
    }

    /**
     * Poll for unpublished outbox events and publish them to Kafka.
     *
     * <p>Each event is published to its routed topic with the aggregate ID
     * as the Kafka message key (for partition affinity). On successful
     * publication, the event is marked as published with a timestamp.
     * Failed publications are logged and will be retried on the next poll.</p>
     */
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
                String topic = routeTopic(event.getEventType());
                String key = event.getAggregateId();
                String payload = event.getPayload();

                kafkaTemplate.send(topic, key, payload);
                String coreTransactionRoutedTopic = resolveCoreTransactionTopic(event.getEventType());
                if (dualEmitCoreTransactionEnabled && coreTransactionRoutedTopic != null) {
                    kafkaTemplate.send(coreTransactionTopic, key, payload);
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

    /**
     * Route an event type to the appropriate Kafka topic.
     *
     * @param eventType the event type string from the outbox entity
     * @return the Kafka topic name
     */
    static String routeTopic(String eventType) {
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

            case "STOCK_MOVEMENT_REQUESTED" -> "pharmacy.stock.movement.requested";

            case "STOCK_MOVEMENT_RECORDED",
                 "STOCK_REVERSAL_RECORDED",
                 "RETURN_RECORDED",
                 "WASTAGE_RECORDED" -> "pharmacy.stock.movement.recorded";

            case "REVERSAL_COMPLETED" -> "pharmacy.reversal.completed";

            case "PICKUP_CLAIMED" -> "pharmacy.pickup.claimed";

            // OF-B16: uncollected-pickup escalation (episode cancelled + stock return)
            case "PICKUP_EXPIRED_RETURN" -> "pharmacy.pickup.expired";

            // OF-B15: substitution → prescriber clarification loop (§8.10.1)
            case "DISPENSE_CLARIFICATION_REQUESTED",
                 "DISPENSE_CLARIFICATION_RESOLVED" -> "pharmacy.dispense.clarification.v1";

            // OF-B12: §13.3.1 claim-to-dispense cross-matching anomalies
            case "DISPENSE_CLAIM_ANOMALY" -> "pharmacy.dispense.claim.anomaly.v1";

            case "MUSHEX_CHARGE_REQUESTED" -> "pharmacy.mushex.charge";

            case "RECONCILE_RESOLVED" -> "pharmacy.reconcile.updated";

            case "PRESCRIPTION_CREATED",
                 "PRESCRIPTION_REFILL_REQUESTED" -> "pharmacy.prescription.updated";

            case "PRESCRIPTION_CANCELLED" -> "pharmacy.prescription.cancelled";

            case "PRESCRIPTION_DISPENSED" -> "pharmacy.prescription.dispensed";

            default -> "pharmacy.events";
        };
    }

    static String resolveCoreTransactionTopic(String eventType) {
        if (eventType == null) {
            return null;
        }
        return switch (eventType) {
            case "PRESCRIPTION_CREATED",
                 "DISPENSE_ORDER_RECEIVED",
                 "DISPENSE_ACCEPTED",
                 "DISPENSE_COMPLETED",
                 "DISPENSE_CANCELLED",
                 "PICKUP_CLAIMED",
                 "MUSHEX_CHARGE_REQUESTED",
                 "PRESCRIPTION_DISPENSED" -> "core.transaction.events";
            default -> null;
        };
    }
}
