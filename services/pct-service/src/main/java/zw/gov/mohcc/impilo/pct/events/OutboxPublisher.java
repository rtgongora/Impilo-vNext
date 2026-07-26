package zw.gov.mohcc.impilo.pct.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Scheduled outbox publisher that polls for unpublished events and
 * publishes them to Kafka topics.
 *
 * <p>Implements the transactional outbox pattern: domain events are
 * written to the {@code event_outbox} table within the same database
 * transaction as the domain change, ensuring at-least-once delivery
 * to Kafka without requiring distributed transactions.</p>
 *
 * <p>Topic routing is determined by the event type:</p>
 * <table>
 *   <tr><th>Event Type</th><th>Kafka Topic</th></tr>
 *   <tr><td>JOURNEY_CREATED, JOURNEY_STATE_CHANGED</td><td>pct.journey.state_changed</td></tr>
 *   <tr><td>QUEUE_ITEM_CREATED, QUEUE_ITEM_UPDATED, QUEUE_ITEM_ENQUEUED, QUEUE_ITEM_CALLED, QUEUE_ITEM_TRANSFERRED</td><td>pct.queue.item.updated</td></tr>
 *   <tr><td>ENCOUNTER_STARTED</td><td>pct.encounter.started</td></tr>
 *   <tr><td>ENCOUNTER_COMPLETED</td><td>pct.encounter.completed</td></tr>
 *   <tr><td>TELECONSULT_COMPLETED</td><td>clinical.teleconsult.value</td></tr>
 *   <tr><td>ADMISSION_CREATED, ADMISSION_UPDATED, ADMISSION_REQUESTED, PATIENT_ADMITTED</td><td>pct.admission.updated</td></tr>
 *   <tr><td>DISCHARGE_INITIATED, DISCHARGE_STARTED</td><td>pct.discharge.started</td></tr>
 *   <tr><td>DISCHARGE_COMPLETED</td><td>pct.discharge.completed</td></tr>
 *   <tr><td>DEATH_RECORDED</td><td>pct.death.recorded</td></tr>
 *   <tr><td>DEATH_CASE_COMPLETED, DEATH_COMPLETED</td><td>pct.death.completed</td></tr>
 *   <tr><td>TASK_CREATED, TASK_COMPLETED</td><td>pct.task.updated</td></tr>
 *   <tr><td>default</td><td>pct.events</td></tr>
 * </table>
 *
 * <p>The publisher runs on a fixed-delay schedule (default 500ms) and
 * processes events in batches to balance throughput and latency.</p>
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    /** Maximum number of events to process per poll cycle. */
    private static final int BATCH_SIZE = 100;

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final boolean dualEmitCanonicalEnabled;
    private final boolean dualEmitCoreTransactionEnabled;
    private final String coreTransactionTopic;
    private final String canonicalJourneyCompletedTopic;
    private final String canonicalEncounterCompletedTopic;
    private final String canonicalDeathRecordedTopic;

    public OutboxPublisher(EventOutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           @Value("${pct.outbox.dual-emit-canonical-enabled:false}") boolean dualEmitCanonicalEnabled,
                           @Value("${pct.outbox.dual-emit-core-transaction-enabled:true}") boolean dualEmitCoreTransactionEnabled,
                           @Value("${pct.outbox.core-transaction-topic:core.transaction.events}") String coreTransactionTopic,
                           @Value("${pct.outbox.canonical-topics.journey-completed:clinical.pct.journey.completed}") String canonicalJourneyCompletedTopic,
                           @Value("${pct.outbox.canonical-topics.encounter-completed:clinical.pct.encounter.completed}") String canonicalEncounterCompletedTopic,
                           @Value("${pct.outbox.canonical-topics.death-recorded:clinical.pct.death.recorded}") String canonicalDeathRecordedTopic) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.dualEmitCanonicalEnabled = dualEmitCanonicalEnabled;
        this.dualEmitCoreTransactionEnabled = dualEmitCoreTransactionEnabled;
        this.coreTransactionTopic = coreTransactionTopic;
        this.canonicalJourneyCompletedTopic = canonicalJourneyCompletedTopic;
        this.canonicalEncounterCompletedTopic = canonicalEncounterCompletedTopic;
        this.canonicalDeathRecordedTopic = canonicalDeathRecordedTopic;
    }

    /**
     * Poll for unpublished outbox events and publish them to Kafka.
     *
     * <p>Each event is published to its routed topic with the aggregate ID
     * as the Kafka message key (for partition affinity). On successful
     * publication, the event is marked as published with a timestamp.
     * Failed publications are logged and will be retried on the next poll.</p>
     */
    @Scheduled(fixedDelayString = "${pct.outbox.poll-interval-ms:500}")
    @Transactional
    public void publishPendingEvents() {
        List<EventOutboxEntity> pending = outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        if (pending.isEmpty()) {
            return;
        }

        // Limit batch size to prevent long-running transactions
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
                String canonicalTopic = resolveCanonicalCompanionTopic(event.getEventType());
                if (canonicalTopic != null && !canonicalTopic.equals(topic)) {
                    kafkaTemplate.send(canonicalTopic, key, payload);
                }
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
                // Skip this event and continue with the next; it will be retried on the next poll
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
            return "pct.events";
        }
        if (eventType.startsWith("telemedicine.session.")) {
            return "clinical.teleconsult.lifecycle";
        }
        // telemed->value: a completed teleconsult is a billable service event consumed by COSTA/L4.
        if ("TELECONSULT_COMPLETED".equals(eventType)) {
            return "clinical.teleconsult.value";
        }

        return switch (eventType) {
            case "JOURNEY_CREATED", "JOURNEY_STATE_CHANGED" -> "pct.journey.state_changed";

            case "QUEUE_ITEM_CREATED", "QUEUE_ITEM_UPDATED",
                 "QUEUE_ITEM_ENQUEUED", "QUEUE_ITEM_CALLED",
                 "QUEUE_ITEM_TRANSFERRED", "QUEUE_ITEM_ESCALATED" -> "pct.queue.item.updated";

            case "ENCOUNTER_STARTED" -> "pct.encounter.started";
            case "ENCOUNTER_COMPLETED" -> "pct.encounter.completed";

            case "TELECONSULT_COMPLETED" -> "clinical.teleconsult.value";

            case "ADMISSION_CREATED", "ADMISSION_UPDATED",
                 "ADMISSION_REQUESTED", "ADMISSION_APPROVED", "PATIENT_ADMITTED" -> "pct.admission.updated";

            case "DISCHARGE_INITIATED", "DISCHARGE_STARTED" -> "pct.discharge.started";
            case "DISCHARGE_COMPLETED" -> "pct.discharge.completed";

            case "DEATH_RECORDED" -> "pct.death.recorded";
            case "DEATH_CASE_COMPLETED", "DEATH_COMPLETED" -> "pct.death.completed";

            case "TASK_CREATED", "TASK_COMPLETED" -> "pct.task.updated";

            case "TRIAGE_RECORDED" -> "pct.triage.recorded";

            // Observations get their own topic because BUTANO subscribes to them specifically to
            // archive into the shared health record. Left on the pct.events catch-all the SHR
            // bridge would either miss them entirely or have to filter every PCT event to find
            // them — and it missed them entirely, which is how this route came to be added.
            case "pct.observation.recorded" -> "pct.observation.recorded";

            // A birth is the one clinical event that other planes must act on rather than merely
            // record: VITO asserts the mother-child relationship from it, UBOMI raises the civil
            // birth notification a human then attests, and BUTANO archives the birth summary.
            // Left on the catch-all — where it sat until now — each of those consumers would have
            // to filter every PCT event to find a birth, which in practice means none of them
            // subscribed at all.
            case "pct.newborn.episode.opened", "pct.newborn.record.updated" -> "pct.newborn.episode.opened";

            // The estate's only ED safety event. EdDiagnosticsService has emitted
            // pct.ed.critical_result since the ED diagnostics lane was built, but with no route
            // case here it landed on the pct.events catch-all — so rito (safety signal) and
            // notification (clinician paging) had to filter every PCT event to find a critical
            // result, which means neither subscribed. A critical result nobody is told about is
            // the precise failure the acknowledge-and-act loop exists to prevent.
            case "pct.ed.critical_result" -> "pct.emergency.critical_result";

            case "TRANSFER_REQUESTED", "TRANSFER_COMPLETED" -> "pct.transfer.updated";

            // Nutrition programme lifecycle. Tracing gets its own topic because it is acted on by a
            // different team from the one that recorded the review — a community health worker doing
            // a home visit, not the nurse at the clinic — and often on a different day.
            case "IMAM_EPISODE_ENROLLED", "IMAM_EPISODE_CLOSED" -> "pct.imam.episode.updated";
            case "IMAM_VISIT_RECORDED" -> "pct.imam.visit.recorded";
            case "IMAM_TRACING_REQUIRED" -> "pct.imam.tracing.required";

            // Virtual-pool substrate: TUSO consumes pool.materialized to flip
            // ACTIVATION_REQUESTED -> ACTIVE; comms plane consumes sla.breached.
            case "POOL_MATERIALIZED" -> "pct.telemedicine.pool.materialized";
            case "QUEUE_SLA_BREACHED" -> "pct.telemedicine.queue.sla.breached";

            default -> "pct.events";
        };
    }

    private String resolveCanonicalCompanionTopic(String eventType) {
        if (!dualEmitCanonicalEnabled || eventType == null) {
            return null;
        }
        return switch (eventType) {
            case "JOURNEY_STATE_CHANGED" -> canonicalJourneyCompletedTopic;
            case "ENCOUNTER_COMPLETED" -> canonicalEncounterCompletedTopic;
            case "DEATH_RECORDED" -> canonicalDeathRecordedTopic;
            default -> null;
        };
    }

    private static String resolveCoreTransactionTopic(String eventType) {
        if (eventType == null) {
            return null;
        }
        return switch (eventType) {
            case "JOURNEY_CREATED",
                 "JOURNEY_STATE_CHANGED",
                 "TRIAGE_RECORDED",
                 "QUEUE_ITEM_ENQUEUED",
                 "QUEUE_ITEM_CALLED",
                 "ENCOUNTER_STARTED",
                 "ENCOUNTER_COMPLETED",
                 "DISCHARGE_COMPLETED",
                 "TASK_CREATED",
                 "TASK_COMPLETED",
                 "TELECONSULT_COMPLETED" -> "core.transaction.events";
            default -> null;
        };
    }
}
