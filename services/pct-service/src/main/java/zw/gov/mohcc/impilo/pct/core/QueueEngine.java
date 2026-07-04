package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.domain.JourneyState;
import zw.gov.mohcc.impilo.pct.domain.QueueItemStatus;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.JourneyEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.QueueItemEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.JourneyRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.QueueItemRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Core queue management engine for the Patient Care Tracker.
 *
 * <p>Manages the lifecycle of queue items from enqueue through calling,
 * service, and completion. Each queue is identified by a UUID and can
 * contain items with different priorities. Items are served in
 * priority-descending, creation-time-ascending order (highest acuity
 * patients first, FIFO within the same priority).</p>
 *
 * <p>Token numbers are auto-generated per queue as a monotonically
 * increasing integer, providing a human-friendly reference for patient
 * displays and announcements.</p>
 *
 * <p>Queue state changes that affect the patient journey (e.g. starting
 * service, marking no-show) automatically trigger the appropriate journey
 * state transition via the {@link JourneyStateMachine}.</p>
 */
@Service
public class QueueEngine {

    private static final Logger log = LoggerFactory.getLogger(QueueEngine.class);

    private final QueueItemRepository queueItemRepository;
    private final JourneyRepository journeyRepository;
    private final JourneyStateMachine journeyStateMachine;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public QueueEngine(QueueItemRepository queueItemRepository,
                       JourneyRepository journeyRepository,
                       JourneyStateMachine journeyStateMachine,
                       EventOutboxRepository outboxRepository,
                       ObjectMapper objectMapper) {
        this.queueItemRepository = queueItemRepository;
        this.journeyRepository = journeyRepository;
        this.journeyStateMachine = journeyStateMachine;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Add a patient journey to a queue.
     *
     * <p>Creates a new queue item with an auto-generated token number
     * (max existing + 1 for that queue), status {@link QueueItemStatus#WAITING},
     * and the specified priority. The associated journey is transitioned to
     * {@link JourneyState#QUEUED}.</p>
     *
     * @param queueId   the target queue identifier
     * @param journeyId the patient journey to enqueue
     * @param priority  the priority level (1 = lowest, 5 = highest / most urgent)
     * @return the created queue item with assigned token number
     */
    @Transactional
    public QueueItemEntity enqueue(UUID queueId, String journeyId, int priority) {
        TrustContext ctx = TrustContextHolder.require();

        JourneyEntity journey = journeyRepository.findByJourneyId(journeyId)
                .orElseThrow(() -> new IllegalArgumentException("Journey not found: " + journeyId));

        // Generate token number: max existing + 1 for this queue
        int maxToken = queueItemRepository.findMaxTokenNumberByQueueId(queueId);
        int tokenNumber = maxToken + 1;

        QueueItemEntity item = new QueueItemEntity();
        item.setId(UUID.randomUUID());
        item.setQueueId(queueId);
        item.setJourneyId(journeyId);
        item.setPatientCpid(journey.getPatientCpid());
        item.setTenantId(ctx.tenantId());
        item.setFacilityId(journey.getFacilityId());
        item.setTokenNumber(tokenNumber);
        item.setPriority(priority);
        item.setStatus(QueueItemStatus.WAITING);
        item.setCreatedAt(OffsetDateTime.now());

        item = queueItemRepository.save(item);

        // Transition journey to QUEUED (if not already)
        if (journey.getState() != JourneyState.QUEUED) {
            journeyStateMachine.transition(journey, JourneyState.QUEUED);
        }

        writeOutbox("QUEUE_ITEM", item.getId().toString(), "QUEUE_ITEM_ENQUEUED", toJson(Map.of(
                "queueId", queueId.toString(),
                "journeyId", journeyId,
                "patientCpid", item.getPatientCpid(),
                "tokenNumber", tokenNumber,
                "priority", priority)));

        log.info("Enqueued journey {} to queue {} with token {} (priority {})",
                journeyId, queueId, tokenNumber, priority);

        return item;
    }

    /**
     * Call the next highest-priority waiting item from a queue.
     *
     * <p>Selects the item with the highest priority; within the same priority
     * level, the item that has been waiting the longest (earliest creation
     * time) is selected first. The item status is updated to
     * {@link QueueItemStatus#CALLED} with the current actor recorded as the
     * caller.</p>
     *
     * @param queueId the queue to call from
     * @return the called queue item, or {@code null} if the queue has no waiting items
     */
    @Transactional
    public QueueItemEntity callNext(UUID queueId) {
        TrustContext ctx = TrustContextHolder.require();

        Optional<QueueItemEntity> nextOpt = queueItemRepository
                .findFirstByQueueIdAndStatusOrderByPriorityDescCreatedAtAsc(queueId, QueueItemStatus.WAITING);

        if (nextOpt.isEmpty()) {
            log.debug("No waiting items in queue {}", queueId);
            return null;
        }

        QueueItemEntity item = nextOpt.get();
        item.setStatus(QueueItemStatus.CALLED);
        item.setCalledBy(ctx.actorId());
        item.setCalledAt(OffsetDateTime.now());

        item = queueItemRepository.save(item);

        writeOutbox("QUEUE_ITEM", item.getId().toString(), "QUEUE_ITEM_CALLED", toJson(Map.of(
                "queueId", queueId.toString(),
                "journeyId", item.getJourneyId(),
                "patientCpid", item.getPatientCpid(),
                "tokenNumber", item.getTokenNumber(),
                "calledBy", ctx.actorId())));

        log.info("Called token {} (journey {}) from queue {}",
                item.getTokenNumber(), item.getJourneyId(), queueId);

        return item;
    }

    /**
     * Update the status of a specific queue item.
     *
     * <p>Handles timestamp logic for each status transition and triggers
     * the appropriate journey state change where applicable:</p>
     * <ul>
     *   <li>{@code CALLED} — sets {@code calledBy} and {@code calledAt}</li>
     *   <li>{@code IN_SERVICE} — sets {@code serviceStartedAt}, transitions journey to {@code IN_SERVICE}</li>
     *   <li>{@code COMPLETED} — sets {@code completedAt}</li>
     *   <li>{@code NO_SHOW} — sets {@code completedAt}, transitions journey to {@code NO_SHOW}</li>
     *   <li>{@code LEFT} — sets {@code completedAt}, transitions journey to {@code LEFT_WITHOUT_BEING_SEEN}</li>
     * </ul>
     *
     * @param itemId    the queue item identifier
     * @param newStatus the new status to set
     * @return the updated queue item
     * @throws IllegalArgumentException if the item is not found
     */
    @Transactional
    public QueueItemEntity updateItemStatus(UUID itemId, QueueItemStatus newStatus) {
        TrustContext ctx = TrustContextHolder.require();

        QueueItemEntity item = queueItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Queue item not found: " + itemId));
        String journeyId = item.getJourneyId();
        QueueItemStatus previousStatus = item.getStatus();

        item.setStatus(newStatus);

        switch (newStatus) {
            case CALLED -> {
                item.setCalledBy(ctx.actorId());
                item.setCalledAt(OffsetDateTime.now());
            }
            case IN_TRIAGE -> {
                // Pulling a patient into triage is a call: record the caller
                // unless the item was already CALLED. Journey-level triage
                // truth stays with TriageService (journey → TRIAGED on
                // completion); the queue item merely tracks occupancy.
                if (item.getCalledAt() == null) {
                    item.setCalledBy(ctx.actorId());
                    item.setCalledAt(OffsetDateTime.now());
                }
            }
            case IN_SERVICE -> {
                item.setServiceStartedAt(OffsetDateTime.now());
                JourneyEntity journey = journeyRepository.findByJourneyId(journeyId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Journey not found for queue item: " + journeyId));
                journeyStateMachine.transition(journey, JourneyState.IN_SERVICE);
            }
            case COMPLETED -> {
                item.setCompletedAt(OffsetDateTime.now());
            }
            case NO_SHOW -> {
                item.setCompletedAt(OffsetDateTime.now());
                JourneyEntity journey = journeyRepository.findByJourneyId(journeyId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Journey not found for queue item: " + journeyId));
                journeyStateMachine.transition(journey, JourneyState.NO_SHOW);
            }
            case LEFT -> {
                item.setCompletedAt(OffsetDateTime.now());
                JourneyEntity journey = journeyRepository.findByJourneyId(journeyId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Journey not found for queue item: " + journeyId));
                journeyStateMachine.transition(journey, JourneyState.LEFT_WITHOUT_BEING_SEEN);
            }
            default -> { /* WAITING, TRANSFERRED — no special timestamp logic */ }
        }

        item = queueItemRepository.save(item);

        // Every status transition is auditable and consumable downstream
        // (pct.queue.item.updated); silent state changes are forbidden.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("queueId", item.getQueueId().toString());
        payload.put("journeyId", journeyId);
        payload.put("patientCpid", item.getPatientCpid());
        payload.put("tokenNumber", item.getTokenNumber());
        payload.put("previousStatus", previousStatus != null ? previousStatus.name() : null);
        payload.put("newStatus", newStatus.name());
        payload.put("actorId", ctx.actorId());
        writeOutbox("QUEUE_ITEM", item.getId().toString(), "QUEUE_ITEM_UPDATED", toJson(payload));

        log.info("Queue item {} updated to status {}", itemId, newStatus);

        return item;
    }

    /**
     * Transfer a queue item from its current queue to a target queue.
     *
     * <p>Marks the original item as {@link QueueItemStatus#TRANSFERRED} and
     * creates a new item in the target queue with the same journey and
     * priority. A new token number is generated for the target queue.</p>
     *
     * @param itemId        the queue item to transfer
     * @param targetQueueId the destination queue
     * @return the newly created queue item in the target queue
     * @throws IllegalArgumentException if the item is not found
     */
    @Transactional
    public QueueItemEntity transferItem(UUID itemId, UUID targetQueueId) {
        TrustContext ctx = TrustContextHolder.require();

        QueueItemEntity oldItem = queueItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Queue item not found: " + itemId));

        // Mark old item as transferred
        oldItem.setStatus(QueueItemStatus.TRANSFERRED);
        oldItem.setCompletedAt(OffsetDateTime.now());
        queueItemRepository.save(oldItem);

        // Create new item in target queue
        int maxToken = queueItemRepository.findMaxTokenNumberByQueueId(targetQueueId);

        QueueItemEntity newItem = new QueueItemEntity();
        newItem.setId(UUID.randomUUID());
        newItem.setQueueId(targetQueueId);
        newItem.setJourneyId(oldItem.getJourneyId());
        newItem.setPatientCpid(oldItem.getPatientCpid());
        newItem.setTenantId(ctx.tenantId());
        newItem.setFacilityId(oldItem.getFacilityId());
        newItem.setTokenNumber(maxToken + 1);
        newItem.setPriority(oldItem.getPriority());
        newItem.setStatus(QueueItemStatus.WAITING);
        newItem.setCreatedAt(OffsetDateTime.now());

        newItem = queueItemRepository.save(newItem);

        writeOutbox("QUEUE_ITEM", newItem.getId().toString(), "QUEUE_ITEM_TRANSFERRED", toJson(Map.of(
                "fromQueueId", oldItem.getQueueId().toString(),
                "toQueueId", targetQueueId.toString(),
                "journeyId", oldItem.getJourneyId(),
                "patientCpid", newItem.getPatientCpid(),
                "oldItemId", oldItem.getId().toString(),
                "newItemId", newItem.getId().toString(),
                "newTokenNumber", newItem.getTokenNumber())));

        log.info("Transferred queue item {} from queue {} to queue {} (new token {})",
                itemId, oldItem.getQueueId(), targetQueueId, newItem.getTokenNumber());

        return newItem;
    }

    /**
     * Escalate a queue item: raise its urgency, record who/why, and
     * optionally move it to a target queue (e.g. emergency workspace).
     *
     * <p>Priority is bumped to {@code max(current + 2, 5)} unless an explicit
     * override is given — escalation always strictly increases urgency and
     * lands at least at the top of the 1–5 triage-derived scale. The item's
     * status is left untouched so it remains eligible for {@code callNext};
     * escalation is urgency + visibility, not a lifecycle transition.</p>
     *
     * @param itemId           the queue item to escalate
     * @param reason           mandatory operational reason (audited, may be shown to staff)
     * @param targetQueueId    optional destination queue; when different from the
     *                         current queue the item is transferred and the new item
     *                         carries the escalation
     * @param priorityOverride optional explicit priority; when null the default bump applies
     * @return the live (possibly transferred) queue item
     * @throws IllegalArgumentException if the item is not found or reason is blank
     */
    @Transactional
    public QueueItemEntity escalateItem(UUID itemId, String reason,
                                        UUID targetQueueId, Integer priorityOverride) {
        TrustContext ctx = TrustContextHolder.require();

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Escalation reason is required");
        }

        QueueItemEntity item = queueItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Queue item not found: " + itemId));

        UUID fromQueueId = item.getQueueId();
        int newPriority = priorityOverride != null
                ? priorityOverride
                : Math.max(item.getPriority() + 2, 5);

        if (targetQueueId != null && !targetQueueId.equals(fromQueueId)) {
            // Move to the target queue first; the transferred item carries the escalation.
            item = transferItem(itemId, targetQueueId);
        }

        item.setPriority(newPriority);
        item.setEscalatedAt(OffsetDateTime.now());
        item.setEscalatedBy(ctx.actorId());
        item.setEscalationReason(reason);
        item = queueItemRepository.save(item);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("queueId", item.getQueueId().toString());
        payload.put("fromQueueId", fromQueueId.toString());
        payload.put("journeyId", item.getJourneyId());
        payload.put("patientCpid", item.getPatientCpid());
        payload.put("tokenNumber", item.getTokenNumber());
        payload.put("priority", newPriority);
        payload.put("reason", reason);
        payload.put("escalatedBy", ctx.actorId());
        writeOutbox("QUEUE_ITEM", item.getId().toString(), "QUEUE_ITEM_ESCALATED", toJson(payload));

        log.info("Escalated queue item {} (queue {} -> {}, priority {}): {}",
                itemId, fromQueueId, item.getQueueId(), newPriority, reason);

        return item;
    }

    /**
     * Compute real-time statistics for a queue.
     *
     * <p>Returns a map containing:</p>
     * <ul>
     *   <li>{@code queueId} — the queue identifier</li>
     *   <li>{@code waitingCount} — number of items in WAITING status</li>
     *   <li>{@code calledCount} — number of items in CALLED status</li>
     *   <li>{@code inServiceCount} — number of items in IN_SERVICE status</li>
     *   <li>{@code completedCount} — number of items in COMPLETED status</li>
     *   <li>{@code avgWaitMinutes} — average wait time in minutes for currently waiting items</li>
     * </ul>
     *
     * @param queueId the queue to compute stats for
     * @return an unmodifiable map of queue statistics
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getQueueStats(UUID queueId) {
        long waitingCount = queueItemRepository.countByQueueIdAndStatus(queueId, QueueItemStatus.WAITING);
        long calledCount = queueItemRepository.countByQueueIdAndStatus(queueId, QueueItemStatus.CALLED);
        long inServiceCount = queueItemRepository.countByQueueIdAndStatus(queueId, QueueItemStatus.IN_SERVICE);
        long completedCount = queueItemRepository.countByQueueIdAndStatus(queueId, QueueItemStatus.COMPLETED);

        // Calculate average wait time for currently waiting items
        List<QueueItemEntity> waitingItems = queueItemRepository
                .findByQueueIdAndStatus(queueId, QueueItemStatus.WAITING);

        double avgWaitMinutes = waitingItems.stream()
                .mapToLong(item -> Duration.between(
                        item.getCreatedAt().toInstant(), Instant.now()).toMinutes())
                .average()
                .orElse(0.0);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("queueId", queueId);
        stats.put("waitingCount", waitingCount);
        stats.put("calledCount", calledCount);
        stats.put("inServiceCount", inServiceCount);
        stats.put("completedCount", completedCount);
        stats.put("avgWaitMinutes", Math.round(avgWaitMinutes * 100.0) / 100.0);

        return Collections.unmodifiableMap(stats);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private void writeOutbox(String aggregateType, String aggregateId,
                             String eventType, String payloadJson) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setPayload(payloadJson);
        outbox.setTenantId(TrustContextHolder.require().tenantId());
        outboxRepository.save(outbox);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise outbox payload: {}", e.getMessage());
            return "{}";
        }
    }
}
