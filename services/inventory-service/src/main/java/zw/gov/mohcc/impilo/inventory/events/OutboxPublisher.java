package zw.gov.mohcc.impilo.inventory.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inventory.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Scheduled outbox publisher that polls for unpublished events and delivers them to Kafka.
 *
 * <p>Implements the transactional outbox pattern: domain events are written to the
 * {@code inv_event_outbox} table within the same database transaction as the domain
 * change, ensuring at-least-once delivery to Kafka without distributed transactions.</p>
 *
 * <h3>Topic Routing</h3>
 * <table>
 *   <tr><th>Event Type</th><th>Kafka Topic</th></tr>
 *   <tr><td>LEDGER_EVENT_CREATED</td><td>inventory.ledger.event.created</td></tr>
 *   <tr><td>ONHAND_UPDATED</td><td>inventory.onhand.updated</td></tr>
 *   <tr><td>STOCKOUT_RISK</td><td>inventory.stockout.risk</td></tr>
 *   <tr><td>COUNT_COMPLETED</td><td>inventory.count.completed</td></tr>
 *   <tr><td>REQUISITION_CREATED</td><td>inventory.requisition.created</td></tr>
 *   <tr><td>REQUISITION_FULFILLED</td><td>inventory.requisition.fulfilled</td></tr>
 *   <tr><td>MUSHEX_CHARGE_REQUESTED</td><td>inventory.mushex.charge</td></tr>
 *   <tr><td>RECONCILE_RESOLVED</td><td>inventory.reconcile.resolved</td></tr>
 *   <tr><td>default</td><td>inventory.events</td></tr>
 * </table>
 *
 * <p>The publisher runs on a fixed-delay schedule (default 2000ms, configurable via
 * {@code inventory.outbox.poll-interval-ms}) and processes events in batches of up to 100.</p>
 */
@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    /** Maximum number of events to process per poll cycle. */
    private static final int BATCH_SIZE = 100;

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Constructs the OutboxPublisher with the outbox repository and Kafka template.
     *
     * @param outboxRepository repository for outbox event queries and updates
     * @param kafkaTemplate    Kafka template for publishing events
     */
    public OutboxPublisher(EventOutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Poll for unpublished outbox events and publish them to Kafka.
     *
     * <p>Each event is published to its routed topic with the aggregate ID
     * as the Kafka message key (for partition affinity). On successful
     * publication, the event is marked as published with a timestamp.
     * Failed publications are logged and will be retried on the next poll.</p>
     */
    @Scheduled(fixedDelayString = "${inventory.outbox.poll-interval-ms:2000}")
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

                event.setPublishedAt(OffsetDateTime.now());
                outboxRepository.save(event);
                successCount++;

            } catch (Exception e) {
                log.error("Failed to publish outbox event {} (type={}) to Kafka: {}",
                        event.getId(), event.getEventType(), e.getMessage(), e);
            }
        }

        if (successCount > 0) {
            log.info("Published {}/{} inventory outbox events to Kafka", successCount, batch.size());
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
