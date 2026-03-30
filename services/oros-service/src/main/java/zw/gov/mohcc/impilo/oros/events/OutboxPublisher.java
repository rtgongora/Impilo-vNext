package zw.gov.mohcc.impilo.oros.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
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
                           @Value("${oros.v11.emit-mode:#{null}}") String ymlEmitMode) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.emitPolicy = new DualEmitPolicy(ymlEmitMode);
    }

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
            log.info("Published {}/{} outbox events to Kafka", successCount, batch.size());
        }
    }

    static String routeTopic(String eventType) {
        if (eventType == null) {
            return "clinical.oros.events";
        }

        return switch (eventType) {
            case "ORDER_PLACED" -> "clinical.oros.order.placed";

            case "ORDER_STATUS_CHANGED",
                 "ORDER_ACCEPTED", "ORDER_SCHEDULED", "ORDER_IN_PROGRESS",
                 "ORDER_PARTIAL_RESULT", "ORDER_RESULT_AVAILABLE",
                 "ORDER_REVIEWED", "ORDER_RELEASED", "ORDER_COMPLETED",
                 "ORDER_REJECTED", "ORDER_FAILED" -> "clinical.oros.order.status_changed";

            case "ORDER_ROUTED", "ROUTE_RETRY" -> "clinical.oros.order.routed";

            case "ORDER_CANCELLED" -> "clinical.oros.order.cancelled";

            case "WORKSTEP_CHANGED", "WORKSTEP_STARTED", "WORKSTEP_COMPLETED" ->
                    "clinical.oros.workstep.changed";

            case "RESULT_AVAILABLE", "RESULT_POSTED" -> "clinical.oros.result.available";

            case "RESULT_CRITICAL", "CRITICAL_RESULT_POSTED", "RESULT_MARKED_CRITICAL" ->
                    "clinical.oros.result.critical";

            case "ACK_RECEIVED" -> "clinical.oros.ack.received";

            case "ACK_ESCALATION" -> "clinical.oros.ack.escalation";

            case "SLA_BREACHED" -> "clinical.oros.sla.breached";

            case "RECONCILE_MATCHED", "RECONCILE_RESOLVED" -> "clinical.oros.reconcile.updated";

            default -> {
                if (eventType.startsWith("ORDER_ACKNOWLEDGED_")) {
                    yield "clinical.oros.ack.received";
                }
                yield "clinical.oros.events";
            }
        };
    }

    static String routeLegacyTopic(String eventType) {
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
