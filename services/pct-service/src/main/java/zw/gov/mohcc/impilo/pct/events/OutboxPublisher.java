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
    private final String canonicalJourneyCompletedTopic;
    private final String canonicalEncounterCompletedTopic;
    private final String canonicalDeathRecordedTopic;

    public OutboxPublisher(EventOutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           @Value("${pct.outbox.dual-emit-canonical-enabled:false}") boolean dualEmitCanonicalEnabled,
                           @Value("${pct.outbox.canonical-topics.journey-completed:clinical.pct.journey.completed}") String canonicalJourneyCompletedTopic,
                           @Value("${pct.outbox.canonical-topics.encounter-completed:clinical.pct.encounter.completed}") String canonicalEncounterCompletedTopic,
                           @Value("${pct.outbox.canonical-topics.death-recorded:clinical.pct.death.recorded}") String canonicalDeathRecordedTopic) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.dualEmitCanonicalEnabled = dualEmitCanonicalEnabled;
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

        return switch (eventType) {
            case "JOURNEY_CREATED", "JOURNEY_STATE_CHANGED" -> "pct.journey.state_changed";

            case "QUEUE_ITEM_CREATED", "QUEUE_ITEM_UPDATED",
                 "QUEUE_ITEM_ENQUEUED", "QUEUE_ITEM_CALLED",
                 "QUEUE_ITEM_TRANSFERRED" -> "pct.queue.item.updated";

            case "ENCOUNTER_STARTED" -> "pct.encounter.started";
            case "ENCOUNTER_COMPLETED" -> "pct.encounter.completed";

            case "ADMISSION_CREATED", "ADMISSION_UPDATED",
                 "ADMISSION_REQUESTED", "PATIENT_ADMITTED" -> "pct.admission.updated";

            case "DISCHARGE_INITIATED", "DISCHARGE_STARTED" -> "pct.discharge.started";
            case "DISCHARGE_COMPLETED" -> "pct.discharge.completed";

            case "DEATH_RECORDED" -> "pct.death.recorded";
            case "DEATH_CASE_COMPLETED", "DEATH_COMPLETED" -> "pct.death.completed";

            case "TASK_CREATED", "TASK_COMPLETED" -> "pct.task.updated";

            case "TRIAGE_RECORDED" -> "pct.triage.recorded";
            case "TRANSFER_REQUESTED", "TRANSFER_COMPLETED" -> "pct.transfer.updated";

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
}
