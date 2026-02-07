package zw.gov.mohcc.impilo.oros.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Scheduled outbox publisher that polls for unpublished events and delivers them to Kafka.
 *
 * <p>Implements the transactional outbox pattern: domain events are written to the
 * {@code oros_event_outbox} table within the same database transaction as the domain
 * change, ensuring at-least-once delivery to Kafka without distributed transactions.</p>
 *
 * <h3>Topic Routing</h3>
 * <table>
 *   <tr><th>Event Type</th><th>Kafka Topic</th></tr>
 *   <tr><td>ORDER_PLACED</td><td>oros.order.placed</td></tr>
 *   <tr><td>ORDER_STATUS_CHANGED, ORDER_*</td><td>oros.order.status_changed</td></tr>
 *   <tr><td>ORDER_ROUTED</td><td>oros.order.routed</td></tr>
 *   <tr><td>ORDER_CANCELLED</td><td>oros.order.cancelled</td></tr>
 *   <tr><td>WORKSTEP_CHANGED, WORKSTEP_STARTED, WORKSTEP_COMPLETED</td><td>oros.workstep.changed</td></tr>
 *   <tr><td>RESULT_AVAILABLE, RESULT_POSTED</td><td>oros.result.available</td></tr>
 *   <tr><td>RESULT_CRITICAL, CRITICAL_RESULT_POSTED, RESULT_MARKED_CRITICAL</td><td>oros.result.critical</td></tr>
 *   <tr><td>ACK_RECEIVED, ORDER_ACKNOWLEDGED_*</td><td>oros.ack.received</td></tr>
 *   <tr><td>ACK_ESCALATION</td><td>oros.ack.escalation</td></tr>
 *   <tr><td>SLA_BREACHED</td><td>oros.sla.breached</td></tr>
 *   <tr><td>RECONCILE_MATCHED, RECONCILE_RESOLVED</td><td>oros.reconcile.updated</td></tr>
 *   <tr><td>default</td><td>oros.events</td></tr>
 * </table>
 *
 * <p>The publisher runs on a fixed-delay schedule (default 2000ms, configurable via
 * {@code oros.outbox.poll-interval-ms}) and processes events in batches of up to 100.</p>
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
    @Scheduled(fixedDelayString = "${oros.outbox.poll-interval-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        List<EventOutboxEntity> pending = outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

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
            log.info("Published {}/{} outbox events to Kafka", successCount, batch.size());
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
            return "oros.events";
        }

        return switch (eventType) {
            case "ORDER_PLACED" -> "oros.order.placed";

            case "ORDER_STATUS_CHANGED",
                 "ORDER_ACCEPTED", "ORDER_SCHEDULED", "ORDER_IN_PROGRESS",
                 "ORDER_PARTIAL_RESULT", "ORDER_RESULT_AVAILABLE",
                 "ORDER_REVIEWED", "ORDER_RELEASED", "ORDER_COMPLETED",
                 "ORDER_REJECTED", "ORDER_FAILED" -> "oros.order.status_changed";

            case "ORDER_ROUTED", "ROUTE_RETRY" -> "oros.order.routed";

            case "ORDER_CANCELLED" -> "oros.order.cancelled";

            case "WORKSTEP_CHANGED", "WORKSTEP_STARTED", "WORKSTEP_COMPLETED" ->
                    "oros.workstep.changed";

            case "RESULT_AVAILABLE", "RESULT_POSTED" -> "oros.result.available";

            case "RESULT_CRITICAL", "CRITICAL_RESULT_POSTED", "RESULT_MARKED_CRITICAL" ->
                    "oros.result.critical";

            case "ACK_RECEIVED" -> "oros.ack.received";

            case "ACK_ESCALATION" -> "oros.ack.escalation";

            case "SLA_BREACHED" -> "oros.sla.breached";

            case "RECONCILE_MATCHED", "RECONCILE_RESOLVED" -> "oros.reconcile.updated";

            default -> {
                if (eventType.startsWith("ORDER_ACKNOWLEDGED_")) {
                    yield "oros.ack.received";
                }
                yield "oros.events";
            }
        };
    }
}
