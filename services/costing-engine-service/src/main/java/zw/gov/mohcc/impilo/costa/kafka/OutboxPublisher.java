package zw.gov.mohcc.impilo.costa.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.EventOutboxRepository;
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
                           @Value("${costa.v11.emit-mode:#{null}}") String ymlEmitMode) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.emitPolicy = new DualEmitPolicy(ymlEmitMode);
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
                log.error("Failed to publish outbox event {} (type={})",
                        event.getId(), event.getEventType(), e);
            }
        }

        if (successCount > 0) {
            log.info("Published {}/{} outbox events", successCount, batch.size());
        }
    }

    static String routeTopic(String eventType) {
        if (eventType == null) return "kernel.costa.events";
        return switch (eventType) {
            case "BILL_DRAFT_CREATED" -> "kernel.costa.bill.draft_created";
            case "BILL_APPROVAL_REQUESTED" -> "kernel.costa.bill.approval_requested";
            case "BILL_APPROVED" -> "kernel.costa.bill.approved";
            case "BILL_FINALIZED" -> "kernel.costa.bill.finalized";
            case "BILL_VOIDED" -> "kernel.costa.bill.voided";
            case "INVOICE_ISSUED" -> "kernel.costa.invoice.issued";
            case "PAYMENT_INTENT_CREATED" -> "kernel.costa.payment.intent_created";
            case "PAYMENT_STATUS_CHANGED" -> "kernel.costa.payment.status_changed";
            case "REFUND_CREATED" -> "kernel.costa.refund.issued";
            case "CLAIM_PACK_CREATED" -> "kernel.costa.claim.pack_created";
            case "ESTIMATE_CREATED" -> "kernel.costa.estimate.created";
            case "RULESET_PUBLISHED" -> "kernel.costa.ruleset.published";
            default -> "kernel.costa.events";
        };
    }

    static String routeLegacyTopic(String eventType) {
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
            case "REFUND_CREATED" -> "costa.refund.issued";
            case "CLAIM_PACK_CREATED" -> "costa.claim.pack.created";
            case "ESTIMATE_CREATED" -> "costa.estimate.created";
            case "RULESET_PUBLISHED" -> "costa.ruleset.published";
            default -> "costa.events";
        };
    }
}
