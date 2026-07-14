package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.domain.QueueItemStatus;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.QueueEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.QueueItemEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ReferralEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.QueueItemRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.QueueRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Queue operations for VIRTUAL-POOL routed teleconsult referrals.
 *
 * <p>Pool items track a referral ({@code sourceRef = referralId}), not a
 * patient journey — journey-based lifecycle stays in {@link QueueEngine}. On
 * referral submit with {@code routing_kind='POOL'} the referral is enqueued
 * into the pool's materialised queue (priority from urgency, SLA deadline from
 * the queue definition rules); accepting the referral completes the item.
 * When no queue has been materialised for the pool the referral is still
 * listable by pool — the degradation is logged honestly, never faked.</p>
 */
@Service
public class VirtualPoolQueueService {

    private static final Logger log = LoggerFactory.getLogger(VirtualPoolQueueService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final QueueRepository queueRepository;
    private final QueueItemRepository queueItemRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public VirtualPoolQueueService(QueueRepository queueRepository,
                                   QueueItemRepository queueItemRepository,
                                   EventOutboxRepository outboxRepository,
                                   ObjectMapper objectMapper) {
        this.queueRepository = queueRepository;
        this.queueItemRepository = queueItemRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Enqueue a pool-routed referral into the pool's first active materialised
     * queue. Idempotent per referral: an existing live item for the referral is
     * returned unchanged. Returns empty when the pool has no materialised
     * queue (honest degradation — referral stays listable by pool).
     */
    @Transactional
    public Optional<QueueItemEntity> enqueueForReferral(ReferralEntity referral) {
        if (referral == null || !"POOL".equalsIgnoreCase(nullSafe(referral.getRoutingKind()))
                || isBlank(referral.getRoutingPoolId())) {
            return Optional.empty();
        }
        String referralId = referral.getReferralId().toString();
        List<QueueItemEntity> live = queueItemRepository.findByTenantIdAndSourceRefAndStatusIn(
                referral.getTenantId(), referralId,
                List.of(QueueItemStatus.WAITING, QueueItemStatus.CALLED, QueueItemStatus.IN_SERVICE));
        if (!live.isEmpty()) {
            return Optional.of(live.get(0)); // idempotent re-submit
        }
        List<QueueEntity> queues = queueRepository.findByTenantIdAndVirtualPoolIdAndActiveTrueOrderBySourceRefAsc(
                referral.getTenantId(), referral.getRoutingPoolId());
        if (queues.isEmpty()) {
            log.warn("No materialised queue for virtual pool '{}' — referral {} stays pool-listable without a "
                            + "queue item (activate the virtual service so PCT can materialise its queues)",
                    referral.getRoutingPoolId(), referralId);
            return Optional.empty();
        }
        QueueEntity queue = queues.get(0);
        int tokenNumber = queueItemRepository.findMaxTokenNumberByQueueId(queue.getQueueId()) + 1;
        int priority = priorityFromUrgency(referral.getUrgency());

        QueueItemEntity item = new QueueItemEntity();
        item.setId(UUID.randomUUID());
        item.setQueueId(queue.getQueueId());
        item.setTenantId(referral.getTenantId());
        item.setSourceRef(referralId);
        item.setPatientCpid(referral.getPatientCpid());
        item.setPriority(priority);
        item.setStatus(QueueItemStatus.WAITING);
        item.setTokenNumber(tokenNumber);
        item.setCreatedAt(OffsetDateTime.now());
        Integer slaMinutes = slaMinutes(queue);
        if (slaMinutes != null) {
            item.setSlaDueAt(OffsetDateTime.now().plusMinutes(slaMinutes));
        }
        item = queueItemRepository.save(item);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "QUEUE_ITEM_ENQUEUED");
        payload.put("tenantId", referral.getTenantId().toString());
        payload.put("queueId", queue.getQueueId().toString());
        payload.put("virtualPoolId", queue.getVirtualPoolId());
        payload.put("sourceRef", referralId);
        payload.put("patientCpid", referral.getPatientCpid());
        payload.put("tokenNumber", tokenNumber);
        payload.put("priority", priority);
        if (item.getSlaDueAt() != null) payload.put("slaDueAt", item.getSlaDueAt().toString());
        writeOutbox(referral.getTenantId(), "QUEUE_ITEM", item.getId().toString(), "QUEUE_ITEM_ENQUEUED", payload);

        log.info("Enqueued referral {} into virtual-pool queue {} (pool {}, token {}, priority {}, slaDueAt {})",
                referralId, queue.getSourceRef(), queue.getVirtualPoolId(), tokenNumber, priority, item.getSlaDueAt());
        return Optional.of(item);
    }

    /**
     * Complete (dequeue) the live pool item(s) for a referral — called when the
     * referral is accepted. No-op when nothing is queued (referral may predate
     * materialisation).
     */
    @Transactional
    public int completeForReferral(ReferralEntity referral, String completedBy) {
        String referralId = referral.getReferralId().toString();
        List<QueueItemEntity> live = queueItemRepository.findByTenantIdAndSourceRefAndStatusIn(
                referral.getTenantId(), referralId,
                List.of(QueueItemStatus.WAITING, QueueItemStatus.CALLED, QueueItemStatus.IN_SERVICE));
        OffsetDateTime now = OffsetDateTime.now();
        for (QueueItemEntity item : live) {
            QueueItemStatus previous = item.getStatus();
            item.setStatus(QueueItemStatus.COMPLETED);
            item.setCompletedAt(now);
            queueItemRepository.save(item);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventType", "QUEUE_ITEM_UPDATED");
            payload.put("tenantId", referral.getTenantId().toString());
            payload.put("queueId", item.getQueueId().toString());
            payload.put("sourceRef", referralId);
            payload.put("previousStatus", previous.name());
            payload.put("newStatus", QueueItemStatus.COMPLETED.name());
            payload.put("actorId", completedBy == null ? "unknown" : completedBy);
            writeOutbox(referral.getTenantId(), "QUEUE_ITEM", item.getId().toString(), "QUEUE_ITEM_UPDATED", payload);
        }
        if (!live.isEmpty()) {
            log.info("Completed {} virtual-pool queue item(s) for accepted referral {}", live.size(), referralId);
        }
        return live.size();
    }

    /** Pool-level queue statistics (depth, oldest wait, SLA breaches) for the BFF read-back. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> poolQueueStats(UUID tenantId, String poolId) {
        return queueRepository.findByTenantIdAndVirtualPoolIdOrderBySourceRefAsc(tenantId, poolId).stream()
                .map(q -> {
                    long waiting = queueItemRepository.countByQueueIdAndStatus(q.getQueueId(), QueueItemStatus.WAITING);
                    long slaBreaches = queueItemRepository.countByQueueIdAndStatusAndEscalatedAtIsNotNull(
                            q.getQueueId(), QueueItemStatus.WAITING);
                    Long oldestWaitingMinutes = queueItemRepository
                            .findFirstByQueueIdAndStatusOrderByCreatedAtAsc(q.getQueueId(), QueueItemStatus.WAITING)
                            .map(item -> Duration.between(item.getCreatedAt().toInstant(), Instant.now()).toMinutes())
                            .orElse(null);
                    Map<String, Object> stats = new LinkedHashMap<>();
                    stats.put("queueId", q.getQueueId().toString());
                    stats.put("sourceRef", q.getSourceRef());
                    stats.put("queueKey", queueKeyOf(q.getSourceRef()));
                    stats.put("name", q.getName());
                    stats.put("virtualPoolId", q.getVirtualPoolId());
                    stats.put("active", q.isActive());
                    stats.put("materializationStatus", q.getMaterializationStatus());
                    stats.put("depth", waiting);
                    stats.put("oldestWaitingMinutes", oldestWaitingMinutes);
                    stats.put("slaBreaches", slaBreaches);
                    stats.put("slaMinutes", slaMinutes(q));
                    stats.put("lastMaterializedAt",
                            q.getLastMaterializedAt() == null ? null : q.getLastMaterializedAt().toString());
                    return stats;
                })
                .toList();
    }

    /** Priority from teleconsult urgency (1..5 triage-derived scale). */
    static int priorityFromUrgency(String urgency) {
        if (urgency == null) return 2;
        return switch (urgency.trim().toUpperCase(Locale.ROOT)) {
            case "EMERGENCY", "CRITICAL", "STAT" -> 5;
            case "URGENT" -> 4;
            case "PRIORITY", "SEMI_URGENT" -> 3;
            default -> 2; // ROUTINE
        };
    }

    /** SLA minutes from queue definition rules ({@code rules.slaMinutes}), NULL = no SLA. */
    Integer slaMinutes(QueueEntity queue) {
        if (queue.getRules() == null || queue.getRules().isBlank()) return null;
        try {
            Object sla = objectMapper.readValue(queue.getRules(), MAP_TYPE).get("slaMinutes");
            if (sla instanceof Number n && n.intValue() > 0) return n.intValue();
            if (sla instanceof String s && !s.isBlank()) {
                int parsed = Integer.parseInt(s.trim());
                return parsed > 0 ? parsed : null;
            }
        } catch (Exception ignored) {
            // malformed rules never break enqueue
        }
        return null;
    }

    private static String queueKeyOf(String sourceRef) {
        if (sourceRef == null) return null;
        int idx = sourceRef.indexOf(':');
        return idx >= 0 && idx < sourceRef.length() - 1 ? sourceRef.substring(idx + 1) : sourceRef;
    }

    private void writeOutbox(UUID tenantId, String aggregateType, String aggregateId,
                             String eventType, Map<String, Object> payload) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setTenantId(tenantId);
        try {
            outbox.setPayload(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            outbox.setPayload("{}");
        }
        outboxRepository.save(outbox);
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static String nullSafe(String s) { return s == null ? "" : s; }
}
