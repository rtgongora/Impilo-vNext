package zw.gov.mohcc.impilo.costa.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 100;

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(EventOutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${costa.outbox.poll-interval-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        List<EventOutboxEntity> pending = outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        if (pending.isEmpty()) return;

        List<EventOutboxEntity> batch = pending.size() > BATCH_SIZE
                ? pending.subList(0, BATCH_SIZE) : pending;

        int successCount = 0;
        for (EventOutboxEntity event : batch) {
            try {
                String topic = routeTopic(event.getEventType());
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload());
                event.setPublishedAt(OffsetDateTime.now());
                outboxRepository.save(event);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to publish outbox event {} (type={})",
                        event.getId(), event.getEventType(), e);
            }
        }

        if (successCount > 0) {
            log.info("Published {}/{} outbox events", successCount, batch.size());
        }
    }

    static String routeTopic(String eventType) {
        if (eventType == null) return "costa.events";
        return switch (eventType) {
            case "BILL_DRAFT_CREATED" -> "costa.bill.draft.created";
            case "BILL_APPROVAL_REQUESTED" -> "costa.bill.approval.requested";
            case "BILL_APPROVED" -> "costa.bill.approved";
            case "BILL_FINALIZED" -> "costa.bill.finalized";
            case "BILL_VOIDED" -> "costa.bill.voided";
            case "INVOICE_ISSUED" -> "costa.invoice.issued";
            case "PAYMENT_INTENT_CREATED" -> "costa.payment.intent.created";
            case "PAYMENT_STATUS_CHANGED" -> "costa.payment.status_changed";
            case "PAYMENT_ALLOCATED" -> "costa.payment.allocated";
            case "CHARGE_CREATED" -> "costa.charge.created";
            case "INVOICE_REFUND_APPLIED" -> "costa.invoice.refund_applied";
            case "REFUND_CREATED" -> "costa.refund.issued";
            case "CLAIM_PACK_CREATED" -> "costa.claim.pack.created";
            case "ESTIMATE_CREATED" -> "costa.estimate.created";
            case "RULESET_PUBLISHED" -> "costa.ruleset.published";
            default -> "costa.events";
        };
    }
}
