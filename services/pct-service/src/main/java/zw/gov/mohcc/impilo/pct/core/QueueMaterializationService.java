package zw.gov.mohcc.impilo.pct.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.integration.TusoIntegration;
import zw.gov.mohcc.impilo.pct.persistence.entity.QueueEntity;
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

    public QueueMaterializationService(TusoIntegration tusoIntegration, QueueRepository queueRepository) {
        this.tusoIntegration = tusoIntegration;
        this.queueRepository = queueRepository;
    }

    /** Outcome of a reconcile pass. {@code status} is OK or FAILED. */
    public record MaterializationResult(
            UUID facilityId, String status, int created, int updated, int retired, int skipped,
            int tusoDefinitions, String message) {}

    /**
     * Reconcile a facility's queues against TUSO's current queue definitions. Idempotent and safe on
     * replay; failure-safe when TUSO is unavailable (no queues are changed).
     */
    @Transactional
    public MaterializationResult reconcileFacility(UUID tenantId, UUID facilityId) {
        List<Map<String, Object>> defs;
        try {
            defs = tusoIntegration.getQueueDefinitions(facilityId);
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
