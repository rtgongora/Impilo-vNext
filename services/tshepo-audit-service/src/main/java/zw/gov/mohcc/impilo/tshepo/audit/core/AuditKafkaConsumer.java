package zw.gov.mohcc.impilo.tshepo.audit.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.AuditEventRequest;

/**
 * Kafka consumer for audit events published by other TSHEPO services.
 *
 * Listens on topic "tshepo.audit.events" and appends each event to the
 * tamper-evident hash chain. Manual acknowledgment ensures events are
 * only committed after successful chain append.
 */
@Component
public class AuditKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditKafkaConsumer.class);

    private final AuditChainService auditChainService;

    public AuditKafkaConsumer(AuditChainService auditChainService) {
        this.auditChainService = auditChainService;
    }

    /**
     * Consume audit events from the tshepo.audit.events topic.
     * Each event is appended to the hash chain within a transaction.
     * Acknowledgment is only sent after successful persistence.
     *
     * @param request the deserialized audit event
     * @param acknowledgment Kafka manual acknowledgment
     * @param key the Kafka message key (typically correlationId)
     */
    @KafkaListener(
            topics = "tshepo.audit.events",
            groupId = "tshepo-audit",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeAuditEvent(@Payload AuditEventRequest request,
                                   Acknowledgment acknowledgment,
                                   @Header(name = "kafka_receivedMessageKey", required = false) String key) {
        try {
            log.debug("Received audit event from Kafka: type={}, actor={}, correlationId={}, key={}",
                    request.eventType(), request.actorId(), request.correlationId(), key);

            auditChainService.appendEvent(request);

            // Only acknowledge after successful chain append
            acknowledgment.acknowledge();

            log.debug("Successfully processed and acknowledged audit event: correlationId={}",
                    request.correlationId());
        } catch (Exception e) {
            log.error("Failed to process audit event from Kafka: type={}, actor={}, correlationId={}, error={}",
                    request.eventType(), request.actorId(), request.correlationId(), e.getMessage(), e);
            // Do NOT acknowledge — the event will be redelivered
            // This ensures at-least-once delivery semantics for audit events
            throw e;
        }
    }
}
