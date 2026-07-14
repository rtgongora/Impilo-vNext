package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.integration.TusoIntegration;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.QueueEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.QueueRepository;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Materialises PCT queue records FROM TUSO facility service-point/workspace configuration.
 *
 * <p><b>Ownership:</b> queue definitions are owned by TUSO (facility service points/workspaces) and
 * materialised into PCT for care operations. PCT never authors queue definitions; it reconciles them
 * from TUSO — via a config-change event or an on-demand reconcile — so it is robust to missed, delayed,
 * or replayed events.</p>
 *
 * <p><b>Safety invariants:</b> materialisation is idempotent (keyed by the TUSO source reference, so a
 * replay never duplicates a queue); a queue TUSO no longer publishes is <em>retired</em> (marked
 * inactive + RETIRED), never deleted, so active work is not broken; and a failed/invalid TUSO response
 * leaves existing queues untouched (nothing is wiped when TUSO is unreachable).</p>
 */
@Service
public class QueueMaterializationService {

    private static final Logger log = LoggerFactory.getLogger(QueueMaterializationService.class);

    private final TusoIntegration tusoIntegration;
    private final QueueRepository queueRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public QueueMaterializationService(TusoIntegration tusoIntegration, QueueRepository queueRepository,
                                       EventOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.tusoIntegration = tusoIntegration;
        this.queueRepository = queueRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /** Outcome of a reconcile pass. {@code status} is OK or FAILED. */
    public record MaterializationResult(
            UUID facilityId, String status, int created, int updated, int retired, int skipped,
            int tusoDefinitions, String message) {}

    /** Outcome of a virtual-pool reconcile pass for one virtual service entity. */
    public record PoolMaterializationResult(
            String vsUid, String poolId, String status, int created, int updated, int skipped,
            int queueDefinitions, String message) {}

    /**
     * Reconcile a facility's queues against TUSO's current queue definitions. Idempotent and safe on
     * replay; failure-safe when TUSO is unavailable (no queues are changed).
     */
    @Transactional
    public MaterializationResult reconcileFacility(UUID tenantId, UUID facilityId) {
        List<Map<String, Object>> defs;
        try {
            defs = tusoIntegration.getQueueDefinitions(tenantId, facilityId);
        } catch (Exception e) {
            log.warn("Queue materialisation aborted for facility {}: TUSO call failed: {}", facilityId, e.getMessage());
            return failed(facilityId, "TUSO call failed: " + e.getMessage());
        }
        if (defs == null) {
            // NULL = TUSO unavailable / invalid response. Do NOT touch existing queues (runtime safety).
            log.warn("Queue materialisation aborted for facility {}: TUSO returned no usable response", facilityId);
            return failed(facilityId, "TUSO unavailable or invalid response — existing queues left untouched");
        }

        OffsetDateTime now = OffsetDateTime.now();
        Set<String> seenRefs = new HashSet<>();
        int created = 0;
        int updated = 0;
        int skipped = 0;

        for (Map<String, Object> def : defs) {
            String sourceRef = str(def.get("sourceRef"));
            String name = str(def.get("name"));
            if (isBlank(sourceRef) || isBlank(name)) {
                skipped++;
                log.warn("Skipping malformed TUSO queue definition for facility {} (missing sourceRef/name): {}",
                        facilityId, def);
                continue;
            }
            seenRefs.add(sourceRef);
            Optional<QueueEntity> existing =
                    queueRepository.findByTenantIdAndFacilityIdAndSourceRef(tenantId, facilityId, sourceRef);
            QueueEntity q = existing.orElseGet(QueueEntity::new);
            boolean isNew = existing.isEmpty();
            if (isNew) {
                q.setQueueId(UUID.randomUUID());
                q.setTenantId(tenantId);
                q.setFacilityId(facilityId);
                q.setSource(QueueEntity.SOURCE_TUSO);
                q.setSourceRef(sourceRef);
            }
            boolean active = !Boolean.FALSE.equals(def.get("active"));
            q.setName(name);
            q.setQueueType(strOr(def.get("queueType"), "FIFO"));
            q.setWorkspaceId(uuidOrNull(def.get("workspaceId")));
            q.setActive(active);
            q.setMaterializationStatus(active ? QueueEntity.STATUS_MATERIALIZED : QueueEntity.STATUS_RETIRED);
            q.setLastMaterializedAt(now);
            q.setTusoUpdatedAt(offsetOrNull(def.get("updatedAt")));
            queueRepository.save(q);
            if (isNew) {
                created++;
            } else {
                updated++;
            }
        }

        // Retire materialised queues no longer present in TUSO — inactive + RETIRED, never deleted.
        int retired = 0;
        for (QueueEntity q : queueRepository.findByTenantIdAndFacilityIdAndSource(
                tenantId, facilityId, QueueEntity.SOURCE_TUSO)) {
            if (q.getSourceRef() != null && !seenRefs.contains(q.getSourceRef())
                    && !QueueEntity.STATUS_RETIRED.equals(q.getMaterializationStatus())) {
                q.setActive(false);
                q.setMaterializationStatus(QueueEntity.STATUS_RETIRED);
                q.setLastMaterializedAt(now);
                queueRepository.save(q);
                retired++;
            }
        }

        log.info("Materialised facility {} queues from TUSO: created={}, updated={}, retired={}, skipped={}, defs={}",
                facilityId, created, updated, retired, skipped, defs.size());
        return new MaterializationResult(facilityId, "OK", created, updated, retired, skipped, defs.size(), null);
    }

    /**
     * Reconcile the tenant's VIRTUAL-POOL queues against TUSO's activatable
     * virtual service-delivery entities (lifecycle ACTIVATION_REQUESTED or
     * ACTIVE — requested entities materialise first; PCT's confirmation event
     * is what flips them ACTIVE on the TUSO side).
     *
     * <p>Same safety invariants as {@link #reconcileFacility}: idempotent by
     * sourceRef ({@code '{vsUid}:{queueKey}'}), retire-never-delete, and
     * fail-safe when TUSO is unreachable (nothing is touched). Emits one
     * {@code POOL_MATERIALIZED} outbox event per successfully reconciled
     * entity ({@code pct.telemedicine.pool.materialized}) so TUSO can flip
     * ACTIVATION_REQUESTED → ACTIVE.</p>
     */
    @Transactional
    public List<PoolMaterializationResult> reconcileVirtualPools(UUID tenantId) {
        List<Map<String, Object>> services;
        try {
            services = tusoIntegration.getVirtualServices(tenantId);
        } catch (Exception e) {
            log.warn("Virtual-pool materialisation aborted: TUSO call failed: {}", e.getMessage());
            return List.of(new PoolMaterializationResult(null, null, "FAILED", 0, 0, 0, 0,
                    "TUSO call failed: " + e.getMessage()));
        }
        if (services == null) {
            log.warn("Virtual-pool materialisation aborted: TUSO returned no usable response");
            return List.of(new PoolMaterializationResult(null, null, "FAILED", 0, 0, 0, 0,
                    "TUSO unavailable or invalid response — existing pool queues left untouched"));
        }

        OffsetDateTime now = OffsetDateTime.now();
        Set<String> seenRefs = new HashSet<>();
        List<PoolMaterializationResult> results = new java.util.ArrayList<>();

        for (Map<String, Object> vs : services) {
            String vsUid = str(vs.get("vsUid") != null ? vs.get("vsUid") : vs.get("id"));
            if (isBlank(vsUid)) {
                log.warn("Skipping malformed TUSO virtual service (missing vsUid): {}", vs);
                continue;
            }
            String poolId = firstActiveSeamTarget(vs);
            if (isBlank(poolId)) {
                // Defensive: TUSO's fail-closed activation should make this impossible.
                results.add(new PoolMaterializationResult(vsUid, null, "SKIPPED", 0, 0, 0, 0,
                        "no active routing seam — nothing to materialise"));
                continue;
            }
            int created = 0;
            int updated = 0;
            int skipped = 0;
            List<Map<String, Object>> defs = listOfMaps(vs.get("queueDefs"));
            for (Map<String, Object> def : defs) {
                String queueKey = str(def.get("queueKey"));
                String name = str(def.get("name"));
                if (isBlank(queueKey) || isBlank(name)) {
                    skipped++;
                    log.warn("Skipping malformed TUSO queue definition for virtual service {}: {}", vsUid, def);
                    continue;
                }
                String sourceRef = vsUid + ":" + queueKey;
                seenRefs.add(sourceRef);
                Optional<QueueEntity> existing =
                        queueRepository.findByTenantIdAndSourceRefAndVirtualPoolIdIsNotNull(tenantId, sourceRef);
                QueueEntity q = existing.orElseGet(QueueEntity::new);
                boolean isNew = existing.isEmpty();
                if (isNew) {
                    q.setQueueId(UUID.randomUUID());
                    q.setTenantId(tenantId);
                    q.setSource(QueueEntity.SOURCE_TUSO);
                    q.setSourceRef(sourceRef);
                }
                q.setVirtualPoolId(poolId);
                q.setFacilityId(null);
                q.setName(name);
                q.setQueueType(strOr(def.get("queueType"), "FIFO"));
                q.setRules(rulesJson(def));
                q.setActive(true);
                q.setMaterializationStatus(QueueEntity.STATUS_MATERIALIZED);
                q.setLastMaterializedAt(now);
                queueRepository.save(q);
                if (isNew) created++; else updated++;
            }
            results.add(new PoolMaterializationResult(vsUid, poolId, "OK", created, updated, skipped,
                    defs.size(), null));
            emitPoolMaterialized(tenantId, vsUid, poolId, created, updated, defs.size() - skipped);
        }

        // Retire virtual-pool queues no longer published by an activatable entity —
        // inactive + RETIRED, never deleted.
        int retired = 0;
        for (QueueEntity q : queueRepository.findByTenantIdAndSourceAndVirtualPoolIdIsNotNull(
                tenantId, QueueEntity.SOURCE_TUSO)) {
            if (q.getSourceRef() != null && !seenRefs.contains(q.getSourceRef())
                    && !QueueEntity.STATUS_RETIRED.equals(q.getMaterializationStatus())) {
                q.setActive(false);
                q.setMaterializationStatus(QueueEntity.STATUS_RETIRED);
                q.setLastMaterializedAt(now);
                queueRepository.save(q);
                retired++;
            }
        }

        log.info("Materialised virtual pools from TUSO for tenant {}: entities={}, retired={}",
                tenantId, results.size(), retired);
        return results;
    }

    /** Pool-scoped queue read-back: materialised queues + live depth for one pool key. */
    @Transactional(readOnly = true)
    public List<QueueEntity> virtualPoolQueues(UUID tenantId, String poolId) {
        return queueRepository.findByTenantIdAndVirtualPoolIdOrderBySourceRefAsc(tenantId, poolId);
    }

    private String firstActiveSeamTarget(Map<String, Object> vs) {
        for (Map<String, Object> seam : listOfMaps(vs.get("routingSeams"))) {
            if (Boolean.TRUE.equals(seam.get("active"))) {
                String target = str(seam.get("targetRef"));
                if (!isBlank(target)) return target;
            }
        }
        // The public shape (currentRoutingSeams) only carries active seams.
        for (Map<String, Object> seam : listOfMaps(vs.get("currentRoutingSeams"))) {
            String target = str(seam.get("targetRef"));
            if (!isBlank(target)) return target;
        }
        return null;
    }

    /** Queue rules JSON; keeps defaultSlaMinutes in step as rules.slaMinutes for the SLA timer. */
    private String rulesJson(Map<String, Object> def) {
        Map<String, Object> rules = new LinkedHashMap<>();
        if (def.get("rules") instanceof Map<?, ?> raw) {
            for (Map.Entry<?, ?> e : raw.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    rules.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
        }
        Object sla = def.get("defaultSlaMinutes");
        if (sla instanceof Number n && !rules.containsKey("slaMinutes")) {
            rules.put("slaMinutes", n.intValue());
        }
        try {
            return objectMapper.writeValueAsString(rules);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** POOL_MATERIALIZED → pct.telemedicine.pool.materialized (TUSO flips ACTIVATION_REQUESTED → ACTIVE). */
    private void emitPoolMaterialized(UUID tenantId, String vsUid, String poolId,
                                      int created, int updated, int materializedQueues) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("VIRTUAL_POOL");
        outbox.setAggregateId(vsUid);
        outbox.setEventType("POOL_MATERIALIZED");
        outbox.setTenantId(tenantId);
        // No null values in event payload maps (EventEnvelope Map.copyOf NPEs).
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "POOL_MATERIALIZED");
        payload.put("tenantId", tenantId.toString());
        payload.put("vsUid", vsUid);
        payload.put("poolId", poolId);
        payload.put("created", created);
        payload.put("updated", updated);
        payload.put("materializedQueues", materializedQueues);
        try {
            outbox.setPayload(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            outbox.setPayload("{}");
        }
        outboxRepository.save(outbox);
    }

    private static List<Map<String, Object>> listOfMaps(Object value) {
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> typed = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : map.entrySet()) {
                        typed.put(String.valueOf(e.getKey()), e.getValue());
                    }
                    out.add(typed);
                }
            }
        }
        return out;
    }

    /** Materialisation-status summary for a facility (counts by status + last materialised time + seed flag). */
    @Transactional(readOnly = true)
    public Map<String, Object> materializationStatus(UUID tenantId, UUID facilityId) {
        List<QueueEntity> all = queueRepository.findByTenantIdAndFacilityId(tenantId, facilityId);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        OffsetDateTime lastMaterialized = null;
        long materialised = 0;
        long seedOnly = 0;
        for (QueueEntity q : all) {
            byStatus.merge(q.getMaterializationStatus(), 1L, Long::sum);
            if (QueueEntity.SOURCE_TUSO.equals(q.getSource())) {
                materialised++;
                if (q.getLastMaterializedAt() != null
                        && (lastMaterialized == null || q.getLastMaterializedAt().isAfter(lastMaterialized))) {
                    lastMaterialized = q.getLastMaterializedAt();
                }
            } else {
                seedOnly++;
            }
        }
        String overall;
        if (all.isEmpty()) {
            overall = QueueEntity.STATUS_MISSING;
        } else if (materialised == 0) {
            overall = QueueEntity.STATUS_SEED_DEMO;
        } else {
            overall = QueueEntity.STATUS_MATERIALIZED;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("facilityId", facilityId);
        summary.put("overall", overall);
        summary.put("totalQueues", all.size());
        summary.put("materialisedFromTuso", materialised);
        summary.put("seedDemoOnly", seedOnly);
        summary.put("lastMaterializedAt", lastMaterialized);
        summary.put("byStatus", byStatus);
        summary.put("note", "Queue definitions are owned by TUSO facility service-point/workspace "
                + "configuration and materialised into PCT for care operations.");
        return summary;
    }

    private MaterializationResult failed(UUID facilityId, String message) {
        return new MaterializationResult(facilityId, "FAILED", 0, 0, 0, 0, 0, message);
    }

    private static String str(Object v) { return v == null ? null : String.valueOf(v); }
    private static String strOr(Object v, String def) { String s = str(v); return isBlank(s) ? def : s; }
    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static UUID uuidOrNull(Object v) {
        String s = str(v);
        if (isBlank(s)) return null;
        try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }

    private static OffsetDateTime offsetOrNull(Object v) {
        String s = str(v);
        if (isBlank(s)) return null;
        try { return OffsetDateTime.parse(s); } catch (Exception e) { return null; }
    }
}
