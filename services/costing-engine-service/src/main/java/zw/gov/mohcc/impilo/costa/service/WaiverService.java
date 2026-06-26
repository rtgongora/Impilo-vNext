package zw.gov.mohcc.impilo.costa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.api.dto.GrantWaiverRequest;
import zw.gov.mohcc.impilo.costa.api.dto.WaiverDecisionRequest;
import zw.gov.mohcc.impilo.costa.api.dto.WaiverResponse;
import zw.gov.mohcc.impilo.costa.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.WaiverEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.WaiverStatus;
import zw.gov.mohcc.impilo.costa.domain.enums.WaiverType;
import zw.gov.mohcc.impilo.costa.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.WaiverRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Discretionary fee-waiver CRUD + approval lifecycle (grant → approve/reject → revoke).
 *
 * <p>Distinct from the rules-driven {@link zw.gov.mohcc.impilo.costa.exemptions.ExemptionEngine}:
 * a waiver is a one-off, per-charge decision with an explicit approval chain, not a standing
 * catalog rule. On approval (and on revocation of an approved waiver) a {@code WAIVER_APPLIED}
 * value-event (C8) is emitted onto {@code core.transaction.events}.
 *
 * <p>Authz enforced upstream (Envoy ext_authz → TSHEPO). Policy (spec, track P): waiver
 * grant/approve is a graduated-friction action; approval should require an officer role
 * distinct from the granter (separation of duties) — enforced by the policy rule, not here.
 */
@Service
public class WaiverService {

    private static final Logger log = LoggerFactory.getLogger(WaiverService.class);

    static final String EVENT_TYPE = "WAIVER_APPLIED";
    static final String AGGREGATE_TYPE = "WAIVER";

    private final WaiverRepository repository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public WaiverService(WaiverRepository repository,
                         EventOutboxRepository outboxRepository,
                         ObjectMapper objectMapper) {
        this.repository = repository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WaiverResponse grant(GrantWaiverRequest req) {
        var ctx = TrustContextHolder.require();
        WaiverType type = parseEnum(WaiverType.class, req.waiverType(), "waiver_type");
        if (type == WaiverType.PARTIAL
                && (req.waivedAmount() == null || req.waivedAmount().signum() <= 0)) {
            throw new IllegalArgumentException("waived_amount must be > 0 for a PARTIAL waiver");
        }

        WaiverEntity e = new WaiverEntity();
        e.setTenantId(ctx.tenantId());
        e.setWaiverReference(nextReference(ctx.tenantId()));
        e.setPatientCpid(req.patientCpid());
        e.setEncounterId(req.encounterId());
        e.setBillId(req.billId());
        e.setChargeId(req.chargeId());
        e.setDeferredChargeId(req.deferredChargeId());
        e.setWaiverType(type);
        e.setWaivedAmount(type == WaiverType.PARTIAL ? req.waivedAmount() : null);
        e.setCurrency(req.currency() != null && !req.currency().isBlank() ? req.currency() : "USD");
        e.setReasonCategory(req.reasonCategory());
        e.setJustification(req.justification());
        e.setStatus(WaiverStatus.GRANTED);
        e.setGrantedBy(ctx.actorId());
        e.setAuditReference(req.auditReference());

        e = repository.save(e);
        log.info("Granted waiver {} ({}) by {}", e.getWaiverReference(), type, ctx.actorId());
        return WaiverResponse.fromEntity(e);
    }

    @Transactional(readOnly = true)
    public List<WaiverResponse> list(String status, String patientCpid) {
        var ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        List<WaiverEntity> rows;
        if (patientCpid != null && !patientCpid.isBlank()) {
            rows = repository.findTop100ByTenantIdAndPatientCpidOrderByCreatedAtDesc(tenantId, patientCpid.trim());
        } else if (status != null && !status.isBlank()) {
            WaiverStatus s = parseEnum(WaiverStatus.class, status, "status");
            rows = repository.findTop100ByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, s);
        } else {
            rows = repository.findTop100ByTenantIdOrderByCreatedAtDesc(tenantId);
        }
        return rows.stream().map(WaiverResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public WaiverResponse require(UUID id) {
        return WaiverResponse.fromEntity(load(id));
    }

    @Transactional
    public WaiverResponse approve(UUID id, WaiverDecisionRequest req) {
        var ctx = TrustContextHolder.require();
        WaiverEntity e = load(id);
        if (e.getStatus() != WaiverStatus.GRANTED) {
            throw new IllegalArgumentException("Only a GRANTED waiver can be approved (was " + e.getStatus() + ")");
        }
        e.setStatus(WaiverStatus.APPROVED);
        e.setApprovedBy(ctx.actorId());
        e.setApprovedAt(OffsetDateTime.now());
        e = repository.save(e);
        publishWaiverApplied(e, "WAIVER_APPROVED");
        log.info("Approved waiver {} by {}", e.getWaiverReference(), ctx.actorId());
        return WaiverResponse.fromEntity(e);
    }

    @Transactional
    public WaiverResponse reject(UUID id, WaiverDecisionRequest req) {
        var ctx = TrustContextHolder.require();
        WaiverEntity e = load(id);
        if (e.getStatus() != WaiverStatus.GRANTED) {
            throw new IllegalArgumentException("Only a GRANTED waiver can be rejected (was " + e.getStatus() + ")");
        }
        e.setStatus(WaiverStatus.REJECTED);
        e.setApprovedBy(ctx.actorId());  // recorded as the deciding officer
        e.setRejectedReason(req != null ? req.reason() : null);
        e = repository.save(e);
        log.info("Rejected waiver {} by {}", e.getWaiverReference(), ctx.actorId());
        return WaiverResponse.fromEntity(e);
    }

    @Transactional
    public WaiverResponse revoke(UUID id, WaiverDecisionRequest req) {
        var ctx = TrustContextHolder.require();
        WaiverEntity e = load(id);
        if (e.getStatus() == WaiverStatus.REVOKED || e.getStatus() == WaiverStatus.REJECTED) {
            throw new IllegalArgumentException("Waiver is already terminal: " + e.getStatus());
        }
        boolean wasApproved = e.getStatus() == WaiverStatus.APPROVED;
        e.setStatus(WaiverStatus.REVOKED);
        e.setRevokedBy(ctx.actorId());
        e.setRevokedAt(OffsetDateTime.now());
        e.setRevokeReason(req != null ? req.reason() : null);
        e = repository.save(e);
        if (wasApproved) {
            // Reverse the applied value: emit a WAIVER_APPLIED reversal so downstream re-bills.
            publishWaiverApplied(e, "WAIVER_REVOKED");
        }
        log.info("Revoked waiver {} by {} (was approved={})", e.getWaiverReference(), ctx.actorId(), wasApproved);
        return WaiverResponse.fromEntity(e);
    }

    private WaiverEntity load(UUID id) {
        var ctx = TrustContextHolder.require();
        return repository.findByWaiverIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Waiver not found: " + id));
    }

    private String nextReference(UUID tenantId) {
        for (int attempt = 0; attempt < 5; attempt++) {
            String ref = "WVR-" + UlidGenerator.generate();
            if (!repository.existsByTenantIdAndWaiverReference(tenantId, ref)) {
                return ref;
            }
        }
        throw new IllegalStateException("Unable to allocate a unique waiver reference");
    }

    /**
     * Enqueue the WAIVER_APPLIED value-event in the SAME transaction as the waiver state change
     * (M3). An outbox-save failure must roll back the approval/revocation so we never commit a
     * waiver that zeroes value with no corresponding value event (silent value leak).
     */
    private void publishWaiverApplied(WaiverEntity e, String sourceServiceEvent) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("waiverId", e.getWaiverId().toString());
        payload.put("waiverReference", e.getWaiverReference());
        payload.put("kind", EVENT_TYPE);
        payload.put("sourceServiceEvent", sourceServiceEvent);
        payload.put("status", e.getStatus().name());
        payload.put("patientCpid", e.getPatientCpid());
        payload.put("encounterId", e.getEncounterId());
        payload.put("billId", e.getBillId());
        payload.put("chargeId", e.getChargeId());
        payload.put("deferredChargeId",
                e.getDeferredChargeId() != null ? e.getDeferredChargeId().toString() : null);
        payload.put("waiverType", e.getWaiverType().name());
        payload.put("amount", e.getWaivedAmount());
        payload.put("currency", e.getCurrency());
        payload.put("payerKind", "WAIVER");
        payload.put("reversal", "WAIVER_REVOKED".equals(sourceServiceEvent));

        EventOutboxEntity ev = new EventOutboxEntity();
        ev.setAggregateType(AGGREGATE_TYPE);
        ev.setAggregateId(e.getWaiverId().toString());
        ev.setEventType(EVENT_TYPE);
        try {
            ev.setPayload(objectMapper.writeValueAsString(payload));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize WAIVER_APPLIED value-event", ex);
        }
        ev.setTenantId(e.getTenantId());
        outboxRepository.save(ev);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String field) {
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid " + field + ": " + raw);
        }
    }
}
