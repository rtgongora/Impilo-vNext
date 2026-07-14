package zw.gov.mohcc.impilo.tuso.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.VirtualServiceEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.VirtualServiceHistoryEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.VirtualServiceQueueDefEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.VirtualServiceRoutingSeamEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.VirtualServiceHistoryRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.VirtualServiceRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Virtual service-delivery registry (virtual hospitals) — governed reads and
 * writes over the TUSO-owned {@code tuso.virtual_service_*} tables.
 *
 * <p>Write doctrine mirrors {@link FacilityService}: every field change records
 * a {@code virtual_service_history} row and emits an outbox event. Lifecycle is
 * fail-closed: activation requires ≥1 ACTIVE routing seam AND ≥1 queue
 * definition, and only moves the entity to {@code ACTIVATION_REQUESTED} — the
 * {@code ACTIVE} flip happens exclusively when PCT confirms pool
 * materialisation ({@code pct.telemedicine.pool.materialized}), so stored state
 * never overstates runtime reality.</p>
 */
@Service
public class VirtualServiceRegistryService {

    private static final Logger log = LoggerFactory.getLogger(VirtualServiceRegistryService.class);
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    /** Outbox aggregate; TusoOutboxPublisher routes it to impilo.tuso.virtual_service. */
    public static final String AGGREGATE = "VIRTUAL_SERVICE";
    public static final String EVENT_UPDATED = "tuso.virtual_service.updated";
    public static final String EVENT_ACTIVATED = "tuso.virtual_service.activated";
    public static final String EVENT_SUSPENDED = "tuso.virtual_service.suspended";

    private final VirtualServiceRepository virtualServiceRepository;
    private final VirtualServiceHistoryRepository historyRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public VirtualServiceRegistryService(VirtualServiceRepository virtualServiceRepository,
                                         VirtualServiceHistoryRepository historyRepository,
                                         EventOutboxRepository outboxRepository,
                                         ObjectMapper objectMapper) {
        this.virtualServiceRepository = virtualServiceRepository;
        this.historyRepository = historyRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(UUID tenantId, List<String> lifecycleStatuses) {
        List<VirtualServiceEntity> rows = (lifecycleStatuses == null || lifecycleStatuses.isEmpty())
                ? virtualServiceRepository.findByTenantIdOrderByVsUidAsc(tenantId)
                : virtualServiceRepository.findByTenantIdAndLifecycleStatusInOrderByVsUidAsc(tenantId, lifecycleStatuses);
        return rows.stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getByUid(UUID tenantId, String vsUid) {
        return toView(require(tenantId, vsUid));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(UUID tenantId, String vsUid) {
        VirtualServiceEntity entity = require(tenantId, vsUid);
        return historyRepository.findTop100ByVirtualServiceIdOrderByChangedAtDesc(entity.getId()).stream()
                .map(h -> {
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    row.put("changeType", h.getChangeType());
                    row.put("fieldName", h.getFieldName());
                    row.put("oldValue", h.getOldValue());
                    row.put("newValue", h.getNewValue());
                    row.put("changedBy", h.getChangedBy());
                    row.put("changedAt", h.getChangedAt() == null ? null : h.getChangedAt().toString());
                    row.put("reason", h.getReason());
                    return (Map<String, Object>) row;
                })
                .toList();
    }

    // ------------------------------------------------------------------ writes

    /**
     * Governed PATCH: seam edits (activate/deactivate/note by seam id or
     * routingType+targetRef) and queue-def edits (name/SLA/rules by queueKey).
     * Each changed field records history; one updated event is emitted.
     */
    @Transactional
    public Map<String, Object> patch(TrustContext ctx, String vsUid, Map<String, Object> request) {
        VirtualServiceEntity entity = require(ctx.tenantId(), vsUid);
        boolean changed = false;

        String name = str(request.get("name"));
        if (name != null && !name.equals(entity.getName())) {
            recordHistory(entity, "UPDATE", "name", entity.getName(), name, ctx.actorId(), null);
            entity.setName(name);
            changed = true;
        }
        String operatingAuthority = str(request.get("operatingAuthority"));
        if (operatingAuthority != null && !operatingAuthority.equals(entity.getOperatingAuthority())) {
            recordHistory(entity, "UPDATE", "operating_authority",
                    entity.getOperatingAuthority(), operatingAuthority, ctx.actorId(), null);
            entity.setOperatingAuthority(operatingAuthority);
            changed = true;
        }

        Object seams = request.get("routingSeams");
        if (seams == null) seams = request.get("seams");
        if (seams instanceof List<?> seamEdits) {
            for (Object raw : seamEdits) {
                if (raw instanceof Map<?, ?> edit && applySeamEdit(ctx, entity, asStringMap(edit))) {
                    changed = true;
                }
            }
        }

        Object queueDefs = request.get("queueDefs");
        if (queueDefs == null) queueDefs = request.get("queues");
        if (queueDefs instanceof List<?> queueEdits) {
            for (Object raw : queueEdits) {
                if (raw instanceof Map<?, ?> edit && applyQueueDefEdit(ctx, entity, asStringMap(edit))) {
                    changed = true;
                }
            }
        }

        if (changed) {
            entity.setVersion(entity.getVersion() + 1);
            virtualServiceRepository.save(entity);
            emitEvent(entity, EVENT_UPDATED, "PATCH");
        }
        return toView(entity);
    }

    /** Add a routing seam (governed write; history + updated event). */
    @Transactional
    public Map<String, Object> addSeam(TrustContext ctx, String vsUid, Map<String, Object> request) {
        VirtualServiceEntity entity = require(ctx.tenantId(), vsUid);
        String routingType = str(request.get("routingType"));
        String targetRef = str(request.get("targetRef"));
        if (routingType == null || targetRef == null || routingType.isBlank() || targetRef.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "routingType and targetRef are required");
        }
        boolean exists = entity.getRoutingSeams().stream().anyMatch(s ->
                routingType.equals(s.getRoutingType()) && targetRef.equals(s.getTargetRef()));
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Routing seam already exists: " + routingType + " -> " + targetRef);
        }
        VirtualServiceRoutingSeamEntity seam = new VirtualServiceRoutingSeamEntity();
        seam.setVirtualService(entity);
        seam.setRoutingType(routingType);
        seam.setTargetRef(targetRef);
        seam.setNote(str(request.get("note")));
        seam.setActive(!Boolean.FALSE.equals(request.get("active")));
        entity.getRoutingSeams().add(seam);
        entity.setVersion(entity.getVersion() + 1);
        recordHistory(entity, "SEAM_ADD", "routing_seam", null,
                routingType + ":" + targetRef + " active=" + seam.isActive(), ctx.actorId(), str(request.get("note")));
        virtualServiceRepository.save(entity);
        emitEvent(entity, EVENT_UPDATED, "SEAM_ADD");
        return toView(entity);
    }

    /**
     * Fail-closed activation: requires ≥1 ACTIVE routing seam AND ≥1 queue
     * definition, otherwise 422. Moves the entity to ACTIVATION_REQUESTED (not
     * ACTIVE) and emits {@code tuso.virtual_service.activated} — PCT reacts by
     * materialising the pool queues, and only its confirmation flips ACTIVE.
     */
    @Transactional
    public Map<String, Object> activate(TrustContext ctx, String vsUid) {
        VirtualServiceEntity entity = require(ctx.tenantId(), vsUid);
        if (VirtualServiceEntity.LIFECYCLE_ACTIVE.equals(entity.getLifecycleStatus())
                || VirtualServiceEntity.LIFECYCLE_ACTIVATION_REQUESTED.equals(entity.getLifecycleStatus())) {
            return toView(entity); // idempotent
        }
        long activeSeams = entity.getRoutingSeams().stream().filter(VirtualServiceRoutingSeamEntity::isActive).count();
        if (activeSeams == 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Activation refused: no active routing seam. A virtual service must be reachable "
                            + "through at least one active routing seam before activation.");
        }
        if (entity.getQueueDefs().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Activation refused: no queue definitions. A virtual service must define at least "
                            + "one queue before activation.");
        }
        String previous = entity.getLifecycleStatus();
        entity.setLifecycleStatus(VirtualServiceEntity.LIFECYCLE_ACTIVATION_REQUESTED);
        entity.setActivatedBy(ctx.actorId());
        entity.setActivatedAt(OffsetDateTime.now());
        entity.setVersion(entity.getVersion() + 1);
        recordHistory(entity, "ACTIVATE", "lifecycle_status", previous,
                VirtualServiceEntity.LIFECYCLE_ACTIVATION_REQUESTED, ctx.actorId(), null);
        virtualServiceRepository.save(entity);
        emitEvent(entity, EVENT_ACTIVATED, "ACTIVATE");
        log.info("Virtual service {} activation requested by {} ({} active seams, {} queue defs)",
                vsUid, ctx.actorId(), activeSeams, entity.getQueueDefs().size());
        return toView(entity);
    }

    /** Suspend a virtual service (history + suspended event). */
    @Transactional
    public Map<String, Object> suspend(TrustContext ctx, String vsUid, String reason) {
        VirtualServiceEntity entity = require(ctx.tenantId(), vsUid);
        if (VirtualServiceEntity.LIFECYCLE_SUSPENDED.equals(entity.getLifecycleStatus())) {
            return toView(entity); // idempotent
        }
        String previous = entity.getLifecycleStatus();
        entity.setLifecycleStatus(VirtualServiceEntity.LIFECYCLE_SUSPENDED);
        entity.setVersion(entity.getVersion() + 1);
        recordHistory(entity, "SUSPEND", "lifecycle_status", previous,
                VirtualServiceEntity.LIFECYCLE_SUSPENDED, ctx.actorId(), reason);
        virtualServiceRepository.save(entity);
        emitEvent(entity, EVENT_SUSPENDED, "SUSPEND");
        return toView(entity);
    }

    /**
     * ACTIVE flip from PCT's pool-materialisation confirmation (consumer thread,
     * no TrustContext). Only flips ACTIVATION_REQUESTED → ACTIVE; every other
     * state is a no-op so replays and late confirmations are harmless.
     */
    @Transactional
    public boolean markActiveFromMaterialization(UUID tenantId, String vsUid, String confirmedBy) {
        VirtualServiceEntity entity = virtualServiceRepository.findByTenantIdAndVsUid(tenantId, vsUid).orElse(null);
        if (entity == null) {
            log.warn("Pool materialisation confirmed for unknown virtual service {} (tenant {}) — ignoring",
                    vsUid, tenantId);
            return false;
        }
        if (!VirtualServiceEntity.LIFECYCLE_ACTIVATION_REQUESTED.equals(entity.getLifecycleStatus())) {
            return false; // idempotent replay / not awaiting confirmation
        }
        entity.setLifecycleStatus(VirtualServiceEntity.LIFECYCLE_ACTIVE);
        entity.setVersion(entity.getVersion() + 1);
        recordHistory(entity, "ACTIVE", "lifecycle_status",
                VirtualServiceEntity.LIFECYCLE_ACTIVATION_REQUESTED, VirtualServiceEntity.LIFECYCLE_ACTIVE,
                confirmedBy == null ? "pct:pool-materialization" : confirmedBy,
                "PCT confirmed pool queue materialisation");
        virtualServiceRepository.save(entity);
        emitEvent(entity, EVENT_UPDATED, "ACTIVE_FLIP");
        log.info("Virtual service {} flipped ACTIVE after PCT pool materialisation", vsUid);
        return true;
    }

    // ------------------------------------------------------------------ helpers

    private boolean applySeamEdit(TrustContext ctx, VirtualServiceEntity entity, Map<String, Object> edit) {
        Long seamId = asLong(edit.get("id"));
        String routingType = str(edit.get("routingType"));
        String targetRef = str(edit.get("targetRef"));
        VirtualServiceRoutingSeamEntity seam = entity.getRoutingSeams().stream()
                .filter(s -> (seamId != null && seamId.equals(s.getId()))
                        || (routingType != null && targetRef != null
                        && routingType.equals(s.getRoutingType()) && targetRef.equals(s.getTargetRef())))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Routing seam not found for edit: " + edit));
        boolean changed = false;
        Object active = edit.get("active");
        if (active instanceof Boolean b && b != seam.isActive()) {
            recordHistory(entity, "SEAM_UPDATE", "routing_seam.active",
                    seam.getRoutingType() + ":" + seam.getTargetRef() + "=" + seam.isActive(),
                    seam.getRoutingType() + ":" + seam.getTargetRef() + "=" + b, ctx.actorId(), null);
            seam.setActive(b);
            changed = true;
        }
        String note = str(edit.get("note"));
        if (note != null && !note.equals(seam.getNote())) {
            recordHistory(entity, "SEAM_UPDATE", "routing_seam.note", seam.getNote(), note, ctx.actorId(), null);
            seam.setNote(note);
            changed = true;
        }
        return changed;
    }

    private boolean applyQueueDefEdit(TrustContext ctx, VirtualServiceEntity entity, Map<String, Object> edit) {
        String queueKey = str(edit.get("queueKey"));
        if (queueKey == null) queueKey = str(edit.get("id"));
        if (queueKey == null || queueKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "queueKey is required for queue-def edits");
        }
        final String key = queueKey;
        VirtualServiceQueueDefEntity def = entity.getQueueDefs().stream()
                .filter(q -> key.equals(q.getQueueKey()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Queue definition not found: " + key));
        boolean changed = false;
        String name = str(edit.get("name"));
        if (name != null && !name.equals(def.getName())) {
            recordHistory(entity, "QUEUE_DEF_UPDATE", key + ".name", def.getName(), name, ctx.actorId(), null);
            def.setName(name);
            changed = true;
        }
        if (edit.containsKey("defaultSlaMinutes")) {
            Integer sla = asInteger(edit.get("defaultSlaMinutes"));
            if (!Objects.equals(sla, def.getDefaultSlaMinutes())) {
                recordHistory(entity, "QUEUE_DEF_UPDATE", key + ".default_sla_minutes",
                        String.valueOf(def.getDefaultSlaMinutes()), String.valueOf(sla), ctx.actorId(), null);
                def.setDefaultSlaMinutes(sla);
                // Keep the materialisable rules document in step so PCT sees the SLA.
                Map<String, Object> rules = readMap(def.getRules());
                if (sla == null) rules.remove("slaMinutes"); else rules.put("slaMinutes", sla);
                def.setRules(writeJson(rules));
                changed = true;
            }
        }
        if (edit.get("rules") instanceof Map<?, ?> rules) {
            String newRules = writeJson(rules);
            if (!newRules.equals(def.getRules())) {
                recordHistory(entity, "QUEUE_DEF_UPDATE", key + ".rules", def.getRules(), newRules,
                        ctx.actorId(), null);
                def.setRules(newRules);
                changed = true;
            }
        }
        return changed;
    }

    private VirtualServiceEntity require(UUID tenantId, String vsUid) {
        return virtualServiceRepository.findByTenantIdAndVsUid(tenantId, vsUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Virtual service not found: " + vsUid));
    }

    private void recordHistory(VirtualServiceEntity entity, String changeType, String field,
                               String oldValue, String newValue, String actor, String reason) {
        VirtualServiceHistoryEntity h = new VirtualServiceHistoryEntity();
        h.setVirtualServiceId(entity.getId());
        h.setChangeType(changeType);
        h.setFieldName(field);
        h.setOldValue(oldValue);
        h.setNewValue(newValue);
        h.setChangedBy(actor == null ? "system" : actor);
        h.setReason(reason);
        historyRepository.save(h);
    }

    /**
     * Trigger-style outbox event (mirrors FacilityConfigurationService's queue
     * reconcile trigger): flat payload with just enough to identify the entity;
     * consumers reconcile from TUSO's current truth. pod_id + idempotency_key
     * are mandatory outbox hygiene (null values poison the publisher drain).
     */
    private void emitEvent(VirtualServiceEntity entity, String eventType, String changeType) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(AGGREGATE);
        event.setAggregateId(entity.getVsUid());
        event.setEventType(eventType);
        event.setTenantId(entity.getTenantId().toString());
        event.setPodId("national-spine");
        event.setSubjectType(AGGREGATE);
        event.setSubjectId(entity.getVsUid());
        event.setIdempotencyKey("tuso:virtual-service:" + entity.getVsUid() + ":" + changeType + ":" + UUID.randomUUID());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("vsUid", entity.getVsUid());
        payload.put("tenantId", entity.getTenantId().toString());
        payload.put("lifecycleStatus", entity.getLifecycleStatus());
        payload.put("changeType", changeType);
        event.setPayload(payload);
        outboxRepository.save(event);
    }

    private Map<String, Object> toView(VirtualServiceEntity e) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("vsUid", e.getVsUid());
        view.put("id", e.getVsUid()); // canonical-document alias
        view.put("tenantId", e.getTenantId().toString());
        view.put("name", e.getName());
        view.put("level", e.getLevel());
        view.put("operatingModel", e.getOperatingModel());
        view.put("operatingAuthority", e.getOperatingAuthority());
        view.put("regulatoryStatus", e.getRegulatoryStatus());
        view.put("purpose", e.getPurpose());
        view.put("catchment", e.getCatchment());
        if (e.getProvinceCode() != null) view.put("provinceCode", e.getProvinceCode());
        view.put("crossBorderParticipation", e.isCrossBorderParticipation());
        view.put("allowedCrossBorderPrivileges", readList(e.getAllowedCrossBorderPrivileges()));
        view.put("billingModels", readList(e.getBillingModels()));
        view.put("sessionModeIds", readList(e.getSessionModeIds()));
        view.put("providerPoolCadres", readList(e.getProviderPoolCadres()));
        view.put("linkedFacilityRoles", readList(e.getLinkedFacilityRoles()));
        view.put("serviceLines", e.getServiceLines().stream().map(VirtualServiceLineName::of).toList());
        view.put("routingSeams", e.getRoutingSeams().stream().map(s -> {
            Map<String, Object> seam = new LinkedHashMap<String, Object>();
            seam.put("id", s.getId());
            seam.put("routingType", s.getRoutingType());
            seam.put("targetRef", s.getTargetRef());
            seam.put("note", s.getNote());
            seam.put("active", s.isActive());
            return seam;
        }).toList());
        view.put("queueDefs", e.getQueueDefs().stream().map(q -> {
            Map<String, Object> def = new LinkedHashMap<String, Object>();
            def.put("queueKey", q.getQueueKey());
            def.put("name", q.getName());
            def.put("defaultSlaMinutes", q.getDefaultSlaMinutes());
            def.put("rules", readMap(q.getRules()));
            def.put("sourceRef", e.getVsUid() + ":" + q.getQueueKey());
            return def;
        }).toList());
        view.put("lifecycleStatus", e.getLifecycleStatus());
        view.put("activatedBy", e.getActivatedBy());
        view.put("activatedAt", e.getActivatedAt() == null ? null : e.getActivatedAt().toString());
        view.put("version", e.getVersion());
        view.put("updatedAt", e.getUpdatedAt() == null ? null : e.getUpdatedAt().toString());
        return view;
    }

    /** Tiny helper so service lines map to plain strings in views. */
    private static final class VirtualServiceLineName {
        static String of(zw.gov.mohcc.impilo.tuso.persistence.entity.VirtualServiceLineEntity line) {
            return line.getName();
        }
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, MAP);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static Integer asInteger(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
