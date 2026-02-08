package zw.gov.mohcc.impilo.mushex.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.mushex.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(EventOutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${mushex.outbox.poll-interval-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        List<EventOutboxEntity> pending =
            outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        if (pending.isEmpty()) return;

        for (EventOutboxEntity event : pending) {
            try {
                String topic = routeTopic(event.getEventType());
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload());
                event.setPublishedAt(OffsetDateTime.now());
                outboxRepository.save(event);
                log.debug("Published event {} to topic {}", event.getId(), topic);
            } catch (Exception e) {
                log.error("Failed to publish outbox event id={} type={}", event.getId(), event.getEventType(), e);
            }
        }
    }

    static String routeTopic(String eventType) {
        return switch (eventType) {
            case "INTENT_CREATED" -> "mushex.payment.intent.created";
            case "STATUS_CHANGED" -> "mushex.payment.status.changed";
            case "REFUND_REQUESTED", "REFUND_COMPLETED", "REFUND_FAILED" -> "mushex.refund.status.changed";
            case "CLAIM_SUBMITTED" -> "mushex.claim.submitted";
            case "CLAIM_ADJUDICATED" -> "mushex.claim.adjudicated";
            case "SETTLEMENT_BATCH_RELEASED" -> "mushex.settlement.batch.released";
            case "FRAUD_FLAGGED" -> "mushex.fraud.flagged";
            case "REMITTANCE_ISSUED" -> "mushex.remittance.issued";
            case "REMITTANCE_CLAIMED" -> "mushex.remittance.claimed";
            default -> "mushex.events";
        };
    }
}
