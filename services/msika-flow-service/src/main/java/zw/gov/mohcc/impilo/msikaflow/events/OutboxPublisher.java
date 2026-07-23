package zw.gov.mohcc.impilo.msikaflow.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final boolean dualEmitCoreTransactionEnabled;
    private final String coreTransactionTopic;

    public OutboxPublisher(EventOutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           @Value("${msika-flow.outbox.dual-emit-core-transaction-enabled:true}") boolean dualEmitCoreTransactionEnabled,
                           @Value("${msika-flow.outbox.core-transaction-topic:core.transaction.events}") String coreTransactionTopic) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.dualEmitCoreTransactionEnabled = dualEmitCoreTransactionEnabled;
        this.coreTransactionTopic = coreTransactionTopic;
    }

    @Scheduled(fixedDelayString = "${msika-flow.outbox.poll-interval-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        List<EventOutboxEntity> pending = outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        if (pending.isEmpty()) {
            return;
        }

        int successCount = 0;
        for (EventOutboxEntity event : pending) {
            try {
                String topic = routeTopic(event.getEventType());
                String key = event.getAggregateId();
                kafkaTemplate.send(topic, key, event.getPayload());
                String coreTransactionRoutedTopic = resolveCoreTransactionTopic(event.getEventType());
                if (dualEmitCoreTransactionEnabled && coreTransactionRoutedTopic != null) {
                    kafkaTemplate.send(coreTransactionTopic, key, event.getPayload());
                }

                event.setPublishedAt(OffsetDateTime.now());
                outboxRepository.save(event);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to publish outbox event id={}: {}", event.getId(), e.getMessage());
            }
        }

        if (successCount > 0) {
            log.info("Published {}/{} outbox events", successCount, pending.size());
        }
    }

    static String routeTopic(String eventType) {
        if (eventType == null) return "msika.flow.events";
        return switch (eventType) {
            case "ORDER_CREATED" -> "msika.flow.order.created";
            case "ORDER_VALIDATED" -> "msika.flow.order.validated";
            case "ORDER_PRICED" -> "msika.flow.order.priced";
            case "ORDER_PAID" -> "msika.flow.order.paid";
            case "ORDER_ROUTED" -> "msika.flow.order.routed";
            case "ORDER_ACCEPTED" -> "msika.flow.order.accepted";
            case "ORDER_COMPLETED" -> "msika.flow.order.completed";
            case "ORDER_CANCELLED" -> "msika.flow.order.cancelled";
            case "ORDER_FAILED" -> "msika.flow.order.failed";
            case "PICKUP_ISSUED" -> "msika.flow.pickup.issued";
            case "PICKUP_CLAIMED" -> "msika.flow.pickup.claimed";
            case "VENDOR_APPLIED" -> "msika.flow.vendor.applied";
            case "VENDOR_APPROVED" -> "msika.flow.vendor.approved";
            case "VENDOR_SUSPENDED" -> "msika.flow.vendor.suspended";
            case "VENDOR_REINSTATED" -> "msika.flow.vendor.reinstated";
            case "VENDOR_REJECTED" -> "msika.flow.vendor.rejected";
            case "VENDOR_ACTOR_BOUND" -> "msika.flow.vendor.actor-bound";
            case "REFUND_REQUESTED" -> "msika.flow.refund.requested";
            case "REFUND_COMPLETED" -> "msika.flow.refund.completed";
            case "SUBSTITUTION_APPROVED" -> "msika.flow.order.validated";
            // OF-B4/OF-B6 marketplace RFO events (Vol II §8.9/§9.3/§9.4)
            case "REQUEST_PUBLISHED" -> "msika.flow.request.published.v1";
            case "OFFER_SUBMITTED", "OFFER_WITHDRAWN" -> "msika.flow.request.offered.v1";
            case "SELECTION_COMMITTED", "SELECTION_FAILED" -> "msika.flow.request.selected.v1";
            case "REQUEST_CANCELLED" -> "msika.flow.request.cancelled.v1";
            case "REQUEST_EXPIRED", "REQUEST_FAILED_NO_OFFERS", "OFFER_EXPIRED" -> "msika.flow.request.expired.v1";
            default -> "msika.flow.events";
        };
    }

    static String resolveCoreTransactionTopic(String eventType) {
        if (eventType == null) return null;
        return switch (eventType) {
            case "ORDER_CREATED",
                 "ORDER_VALIDATED",
                 "ORDER_PAID",
                 "ORDER_ACCEPTED",
                 "ORDER_COMPLETED",
                 "ORDER_CANCELLED",
                 "ORDER_FAILED",
                 "PICKUP_ISSUED",
                 "PICKUP_CLAIMED",
                 "REFUND_COMPLETED" -> "core.transaction.events";
            default -> null;
        };
    }
}
