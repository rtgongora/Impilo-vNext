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
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;
import zw.gov.mohcc.impilo.sharedkernel.events.DualEmitPolicy;
import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;

import java.time.OffsetDateTime;
import java.util.ArrayList;
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
public class OutboxPublisher extends CompanionOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final boolean dualEmitCanonicalEnabled;
    private final boolean dualEmitCoreTransactionEnabled;
    private final String coreTransactionTopic;
    private final String canonicalResultAvailableTopic;

    /**
     * Constructs the OutboxPublisher with the outbox repository and Kafka template.
     *
     * @param outboxRepository repository for outbox event queries and updates
     * @param kafkaTemplate    Kafka template for publishing events
     */
    public OutboxPublisher(EventOutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           @Value("${oros.outbox.dual-emit-canonical-enabled:false}") boolean dualEmitCanonicalEnabled,
                           @Value("${oros.outbox.dual-emit-core-transaction-enabled:true}") boolean dualEmitCoreTransactionEnabled,
                           @Value("${oros.outbox.core-transaction-topic:core.transaction.events}") String coreTransactionTopic,
                           @Value("${oros.outbox.canonical-topics.result-available:clinical.oros.result.available}") String canonicalResultAvailableTopic,
                           @Value("${oros.v11.emit-mode:#{null}}") String ymlEmitMode) {
        super(new DualEmitPolicy(ymlEmitMode), new EventTopicRegistry("oros"));
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.dualEmitCanonicalEnabled = dualEmitCanonicalEnabled;
        this.dualEmitCoreTransactionEnabled = dualEmitCoreTransactionEnabled;
        this.coreTransactionTopic = coreTransactionTopic;
        this.canonicalResultAvailableTopic = canonicalResultAvailableTopic;
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
    public void poll() {
        int published = publishPendingEvents();
        if (published > 0) {
            log.info("Published {} outbox events to Kafka", published);
        }
    }

    @Override
    protected List<? extends OutboxRow> fetchUnpublished() {
        return outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()
                .stream()
                .map(EventOutboxEntity::toOutboxRow)
                .toList();
    }

    @Override
    protected void sendToKafka(String topic, String key, String value) {
        kafkaTemplate.send(topic, key, value);
    }

    @Override
    protected void markPublished(OutboxRow row, OffsetDateTime publishedAt) {
        outboxRepository.findById(row.id()).ifPresent(entity -> {
            entity.setPublishedAt(publishedAt);
            outboxRepository.save(entity);
        });
    }

    @Override
    protected void markFailed(OutboxRow row, String errorMessage) {
        outboxRepository.findById(row.id()).ifPresent(entity -> {
            entity.setPublishError(errorMessage);
            entity.setRetryCount(row.retryCount() + 1);
            outboxRepository.save(entity);
        });
    }

    /** The primary legacy topic, routed exactly as before the conversion. */
    @Override
    protected String resolveLegacyTopic(OutboxRow row) {
        return routeTopic(legacyEventType(row));
    }

    /**
     * oros has always fanned the same payload to more than one legacy topic — a canonical
     * companion topic for results, and the core-transaction stream. The base class routes one
     * legacy topic, so without this the consumers of the other two would have been silently
     * unsubscribed by the conversion.
     */
    @Override
    protected List<String> additionalLegacyTopics(OutboxRow row) {
        String eventType = legacyEventType(row);
        String primary = routeTopic(eventType);
        List<String> extra = new ArrayList<>(2);

        String canonicalTopic = resolveCanonicalCompanionTopic(eventType);
        if (canonicalTopic != null && !canonicalTopic.equals(primary)) {
            extra.add(canonicalTopic);
        }
        if (dualEmitCoreTransactionEnabled && resolveCoreTransactionTopic(eventType) != null) {
            extra.add(coreTransactionTopic);
        }
        return extra;
    }

    /**
     * Topic routing switches on the stored event type, not the canonical one the envelope
     * carries — {@code row.eventType()} is the v1.1 form and would match none of the cases.
     */
    private static String legacyEventType(OutboxRow row) {
        return row instanceof EventOutboxEntity.OrosOutboxRow orosRow
                ? orosRow.legacyEventType()
                : row.eventType();
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

            case "ORDER_DRAFT_CREATED", "ORDER_DRAFT_UPDATED" -> "oros.order.draft";

            case "ORDER_STATUS_CHANGED",
                 "ORDER_ACCEPTED", "ORDER_SCHEDULED", "ORDER_IN_PROGRESS",
                 "ORDER_PARTIAL_RESULT", "ORDER_RESULT_AVAILABLE",
                 "ORDER_REVIEWED", "ORDER_RELEASED", "ORDER_COMPLETED",
                 "ORDER_REJECTED", "ORDER_FAILED",
                 // OF-B1 lifecycle additions (§9.1 targets) ride the status stream
                 "ORDER_PENDING_SIGNATURE", "ORDER_ON_HOLD", "ORDER_REVOKED",
                 "ORDER_EXPIRED", "ORDER_ENTERED_IN_ERROR" -> "oros.order.status_changed";

            case "ORDER_ROUTED", "ROUTE_RETRY" -> "oros.order.routed";

            case "ORDER_CANCELLED" -> "oros.order.cancelled";

            // OF-B1 net-new families (Vol II §18 wire rule <domain>.<aggregate>.<action>.v1)
            case "ORDER_AMENDED" -> "oros.order.amended.v1";
            case "ORDER_REPLACED" -> "oros.order.replaced.v1";

            // OF-B2 prescription family (Vol II §18 locked family oros.prescription.*.v1)
            case "PRESCRIPTION_CREATED" -> "oros.prescription.created.v1";
            case "PRESCRIPTION_SIGNED" -> "oros.prescription.signed.v1";
            case "PRESCRIPTION_ACTIVATED" -> "oros.prescription.activated.v1";
            case "PRESCRIPTION_AMENDED" -> "oros.prescription.amended.v1";
            case "PRESCRIPTION_CANCELLED" -> "oros.prescription.cancelled.v1";
            case "PRESCRIPTION_EXPIRED" -> "oros.prescription.expired.v1";
            case "PRESCRIPTION_CLAIMED", "PRESCRIPTION_CLAIM_RELEASED",
                 // OF-B12: episode binding rides the claim stream (§13.3.1)
                 "PRESCRIPTION_CLAIM_EPISODE_BOUND" -> "oros.prescription.claimed.v1";

            case "WORKSTEP_CHANGED", "WORKSTEP_STARTED", "WORKSTEP_COMPLETED" ->
                    "oros.workstep.changed";

            case "RESULT_AVAILABLE", "RESULT_POSTED",
                 "RESULT_PRELIMINARY", "RESULT_FINAL", "RESULT_AMENDED", "RESULT_ADDENDUM" ->
                    "oros.result.available";

            case "RESULT_RELEASED" -> "oros.result.released";

            case "RESULT_CRITICAL", "CRITICAL_RESULT_POSTED", "RESULT_MARKED_CRITICAL" ->
                    "oros.result.critical";

            case "ACK_RECEIVED", "RESULT_ACKNOWLEDGED", "RESULT_CRITICAL_ACKNOWLEDGED" ->
                    "oros.ack.received";

            case "ACK_ESCALATION" -> "oros.ack.escalation";

            case "SLA_BREACHED" -> "oros.sla.breached";

            case "RECONCILE_MATCHED", "RECONCILE_RESOLVED" -> "oros.reconcile.updated";

            default -> {
                if (eventType.startsWith("ORDER_ACKNOWLEDGED_")) {
                    yield "oros.ack.received";
                }
                if (eventType.startsWith("IMAGING_STATE_")) {
                    yield "oros.imaging.state_changed";
                }
                if (eventType.startsWith("WORKFLOW_STATE_")) {
                    yield "oros.workflow.state_changed";
                }
                if (eventType.startsWith("SPECIMEN_")) {
                    yield "oros.specimen.changed";
                }
                yield "oros.events";
            }
        };
    }

    private String resolveCanonicalCompanionTopic(String eventType) {
        if (!dualEmitCanonicalEnabled || eventType == null) {
            return null;
        }
        return switch (eventType) {
            case "RESULT_AVAILABLE", "RESULT_POSTED" -> canonicalResultAvailableTopic;
            default -> null;
        };
    }

    static String resolveCoreTransactionTopic(String eventType) {
        if (eventType == null) {
            return null;
        }
        return switch (eventType) {
            case "ORDER_PLACED",
                 "ORDER_STATUS_CHANGED",
                 "ORDER_ACCEPTED",
                 "ORDER_COMPLETED",
                 "ORDER_CANCELLED",
                 "RESULT_AVAILABLE",
                 "RESULT_POSTED",
                 "RESULT_CRITICAL",
                 "CRITICAL_RESULT_POSTED" -> "core.transaction.events";
            default -> null;
        };
    }
}
