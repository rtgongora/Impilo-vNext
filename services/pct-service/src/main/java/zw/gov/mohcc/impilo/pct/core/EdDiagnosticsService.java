package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.pct.integration.DaidzaiEpisodeClient;
import zw.gov.mohcc.impilo.pct.integration.OrosIntegration;
import zw.gov.mohcc.impilo.pct.persistence.entity.EdDiagnosticOrderEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EdDiagnosticOrderRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EdVisitRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ED diagnostics → trauma-episode reconciliation (W5, G1.9). When OROS reports a CRITICAL result for
 * a trauma diagnostic order, PCT reconciles it read-through onto the canonical trauma episode
 * (mirroring the theatre J-CS-3 specimen-critical pattern): it resolves the episode from the OROS
 * order's {@code encounter_ref}, projects a DIAGNOSTICS phase onto the episode timeline, emits a
 * {@code pct.ed.critical_result} SAFETY outbox event, and acknowledges the critical result via OROS's
 * own ack endpoint. No OROS code/schema change — OROS is consumed via its API only.
 */
@Service
public class EdDiagnosticsService {

    private static final Logger log = LoggerFactory.getLogger(EdDiagnosticsService.class);

    private final OrosIntegration oros;
    private final EventOutboxRepository outboxRepository;
    private final DaidzaiEpisodeClient daidzaiEpisodes;
    private final ObjectMapper objectMapper;
    private final EdDiagnosticOrderRepository orderLinkRepository;
    private final EdVisitRepository visitRepository;

    public EdDiagnosticsService(OrosIntegration oros, EventOutboxRepository outboxRepository,
                                DaidzaiEpisodeClient daidzaiEpisodes, ObjectMapper objectMapper,
                                EdDiagnosticOrderRepository orderLinkRepository,
                                EdVisitRepository visitRepository) {
        this.oros = oros;
        this.outboxRepository = outboxRepository;
        this.daidzaiEpisodes = daidzaiEpisodes;
        this.objectMapper = objectMapper;
        this.orderLinkRepository = orderLinkRepository;
        this.visitRepository = visitRepository;
    }

    /**
     * Place a trauma ED diagnostic order on OROS (encounter_ref = trauma_episode_id) and keep the
     * authoritative PCT-side link (oros_order_id → episode). Registers a DIAGNOSTICS phase (ORDERED)
     * on the episode. Best-effort — a degraded OROS never blocks the ED workflow.
     */
    @Transactional
    public Map<String, Object> orderDiagnostic(UUID visitId, Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        var visit = visitRepository.findById(visitId)
                .filter(v -> ctx.tenantId().equals(v.getTenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ED visit not found"));
        UUID episodeId = visit.getTraumaEpisodeId();
        String orderType = strOr(body, "LAB", "orderType", "order_type");
        String testCode = str(body, "testCode", "test_code");
        String orderId = oros.placeOrder(orderType, visit.getPatientCpid(),
                episodeId != null ? episodeId.toString() : null,
                strOr(body, "STAT", "priority"), str(body, "clinicalNotes", "clinical_notes"));

        EdDiagnosticOrderEntity link = new EdDiagnosticOrderEntity();
        link.setTenantId(ctx.tenantId());
        link.setVisitId(visitId);
        link.setTraumaEpisodeId(episodeId);
        link.setOrosOrderId(orderId != null ? orderId : "UNPLACED-" + UUID.randomUUID());
        link.setOrderType(orderType);
        link.setTestCode(testCode);
        link.setStatus("PLACED");
        link = orderLinkRepository.save(link);

        if (episodeId != null && orderId != null) {
            daidzaiEpisodes.registerPhase(ctx.tenantId(), episodeId, "DIAGNOSTICS",
                    orderId, "ORDERED", "pct.ed.diagnostic_ordered");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("oros_order_id", orderId);
        out.put("trauma_episode_id", episodeId != null ? episodeId.toString() : null);
        out.put("link_id", link.getId().toString());
        out.put("status", link.getStatus());
        return out;
    }

    /**
     * Reconcile an OROS critical result onto the trauma episode. Idempotency is delegated to the
     * downstream (the daidzai phase register + the OROS ack are both idempotent). Reads the OROS
     * order to resolve the authoritative episode correlation (encounter_ref) rather than trusting the
     * caller.
     *
     * @param body {@code {orderId, resultId, criticalReason, traumaEpisodeId?}}
     */
    @Transactional
    public Map<String, Object> reconcileCriticalResult(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String orderId = str(body, "orderId", "order_id");
        String resultId = str(body, "resultId", "result_id");
        String reason = str(body, "criticalReason", "critical_reason");
        if (orderId == null && resultId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderId or resultId is required");
        }

        // Resolve the episode via the AUTHORITATIVE PCT-side order→episode link. This is necessary
        // because the OROS critical event payload OMITS encounter_ref (honest Gate-1 limitation;
        // oros-payload enrichment is a post-gate follow-up). A best-effort OROS order read + the
        // caller-supplied id are fallbacks only.
        EdDiagnosticOrderEntity link = orderId != null
                ? orderLinkRepository.findByTenantIdAndOrosOrderId(ctx.tenantId(), orderId).orElse(null)
                : null;
        UUID episodeId = link != null ? link.getTraumaEpisodeId() : null;
        if (episodeId == null && orderId != null) {
            episodeId = parseUuid(str(oros.getOrder(orderId), "encounterRef", "encounter_ref"));
        }
        if (episodeId == null) {
            episodeId = parseUuid(str(body, "traumaEpisodeId", "trauma_episode_id"));
        }

        // Project onto the episode timeline (read-model) + emit the SAFETY outbox signal.
        if (episodeId != null) {
            daidzaiEpisodes.registerPhase(ctx.tenantId(), episodeId, "DIAGNOSTICS",
                    resultId != null ? resultId : orderId, "CRITICAL", "pct.ed.critical_result");
        }
        writeSafetyOutbox(ctx.tenantId(), episodeId, orderId, resultId, reason);

        // Best-effort sync to OROS's own order-scoped ack endpoint (audited in OROS). This is a
        // downstream mirror, not the acknowledgement itself — the clinical acknowledgement is the
        // fact that a human called THIS endpoint. It must never gate the local record: a degraded
        // OROS during this call would otherwise leave a genuinely-acknowledged critical result
        // permanently unable to close (see chk_edo_critical_not_closed, V205), which is the same
        // care-first principle this pack applies to every other best-effort peer call.
        boolean orosSynced = orderId != null && oros.acknowledgeCriticalOrder(orderId, reason);

        // Reconcile the PCT link row (read-through projection of OROS truth).
        if (link != null) {
            link.setStatus("ACKED");
            link.setOrosResultId(resultId);
            link.setCriticalReason(reason);
            link.setSafetyEmittedAt(OffsetDateTime.now());
            link.setAckedAt(OffsetDateTime.now());
            try {
                link.setAckedBy(TrustContextHolder.require().actorId());
            } catch (RuntimeException ignored) {
                // System-initiated reconciliation (e.g. a replay) carries no actor; leave it unset
                // rather than fabricate one.
            }
            orderLinkRepository.save(link);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("trauma_episode_id", episodeId != null ? episodeId.toString() : null);
        out.put("order_id", orderId);
        out.put("result_id", resultId);
        out.put("acknowledged", true);
        out.put("oros_synced", orosSynced);
        out.put("safety_signalled", true);
        return out;
    }

    /**
     * Record that a clinician has acted on a diagnostic result (W7a) — distinct from acknowledging
     * it. Acknowledging means seeing the result; acting means doing something about it (starting
     * antibiotics for a positive culture). Both are kept because "we told someone" and "someone did
     * something" are different facts and this table previously recorded neither reliably.
     */
    @Transactional
    public Map<String, Object> actOnDiagnosticOrder(UUID linkId, Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        EdDiagnosticOrderEntity link = orderLinkRepository.findById(linkId)
                .filter(l -> ctx.tenantId().equals(l.getTenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Diagnostic order not found"));
        link.setStatus("ACTED_ON");
        link.setActedAt(OffsetDateTime.now());
        link.setActedBy(str(body, "actedBy", "acted_by") != null
                ? str(body, "actedBy", "acted_by") : ctx.actorId());
        orderLinkRepository.save(link);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", link.getId().toString());
        out.put("status", link.getStatus());
        return out;
    }

    /**
     * Close a diagnostic order (W7a). Refused by {@code chk_edo_critical_not_closed} at the database
     * if this order carries an unacknowledged critical result — the schema is the real guarantee;
     * this mapping just turns that constraint violation into a clear 409 instead of an opaque 500.
     */
    @Transactional
    public Map<String, Object> closeDiagnosticOrder(UUID linkId, Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        EdDiagnosticOrderEntity link = orderLinkRepository.findById(linkId)
                .filter(l -> ctx.tenantId().equals(l.getTenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Diagnostic order not found"));
        String reason = str(body, "closeReason", "close_reason", "reason");
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "close_reason is required");
        }
        boolean cancel = body != null && Boolean.TRUE.equals(body.get("cancelled"));
        link.setStatus(cancel ? "CANCELLED" : "CLOSED");
        link.setClosedAt(OffsetDateTime.now());
        link.setClosedBy(ctx.actorId());
        link.setCloseReason(reason);
        try {
            orderLinkRepository.save(link);
        } catch (org.springframework.dao.DataIntegrityViolationException critical) {
            // chk_edo_critical_not_closed fired: an unacknowledged critical result exists on this
            // order. A clear 409 telling the caller WHY, rather than a 500.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This order has an unacknowledged critical result and cannot be closed — "
                            + "acknowledge it first");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", link.getId().toString());
        out.put("status", link.getStatus());
        return out;
    }

    private void writeSafetyOutbox(UUID tenantId, UUID episodeId, String orderId, String resultId, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("traumaEpisodeId", episodeId != null ? episodeId.toString() : null);
        payload.put("orderId", orderId);
        payload.put("resultId", resultId);
        payload.put("criticalReason", reason);
        payload.put("safety", "CRITICAL_RESULT");
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("TRAUMA_EPISODE");
        outbox.setAggregateId(episodeId != null ? episodeId.toString() : (orderId != null ? orderId : "unknown"));
        outbox.setEventType("pct.ed.critical_result");
        outbox.setTenantId(tenantId);
        try {
            outbox.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            outbox.setPayload("{}");
        }
        outboxRepository.save(outbox);
        log.info("SAFETY outbox pct.ed.critical_result emitted for episode {} (order {}, result {})",
                episodeId, orderId, resultId);
    }

    private UUID parseUuid(String s) {
        if (s == null || s.isBlank()) return null;
        try { return UUID.fromString(s.trim()); } catch (IllegalArgumentException e) { return null; }
    }

    private static String str(Map<String, Object> body, String... keys) {
        if (body == null) return null;
        for (String k : keys) {
            Object v = body.get(k);
            if (v != null && !v.toString().isBlank()) return v.toString();
        }
        return null;
    }

    private static String strOr(Map<String, Object> body, String fallback, String... keys) {
        String v = str(body, keys);
        return v != null ? v : fallback;
    }
}

