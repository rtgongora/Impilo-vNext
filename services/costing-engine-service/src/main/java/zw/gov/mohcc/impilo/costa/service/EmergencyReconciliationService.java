package zw.gov.mohcc.impilo.costa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.api.dto.EmergencyDeferredChargeResponse;
import zw.gov.mohcc.impilo.costa.api.dto.FlagEmergencyDeferredChargeRequest;
import zw.gov.mohcc.impilo.costa.api.dto.LinkConfirmedPersonRequest;
import zw.gov.mohcc.impilo.costa.api.dto.ReconcileDeferredChargeRequest;
import zw.gov.mohcc.impilo.costa.domain.entity.EmergencyDeferredChargeEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.ServiceAccessDecisionEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.ReconciliationState;
import zw.gov.mohcc.impilo.costa.domain.enums.ServiceAccessStatus;
import zw.gov.mohcc.impilo.costa.domain.enums.SettlementRoute;
import zw.gov.mohcc.impilo.costa.domain.repository.EmergencyDeferredChargeRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.ServiceAccessDecisionRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Emergency-override deferred-charge reconciliation (Journey Law 1).
 *
 * <p>Emergency care is NEVER blocked — this is the <em>post-care</em> settle path, not a gate.
 * It lists charges accrued under {@link ServiceAccessStatus#EMERGENCY_OVERRIDE}, links the
 * provisional person to the confirmed person (VITO stays SoR; link recorded, not overwritten),
 * and routes each deferred charge to SETTLE / CLAIM / WAIVER, emitting an
 * {@code EMERGENCY_DEFERRED_CHARGE} value-event (C8) onto {@code core.transaction.events}.
 *
 * <p>Authz is enforced upstream (Envoy ext_authz → TSHEPO); this service requires a populated
 * trust context. Policy rule (spec-only, track P): {@code EMERGENCY-CARE-NEVER-BLOCKED} — the
 * reconcile step never blocks care, only settles value after the fact.
 */
@Service
public class EmergencyReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(EmergencyReconciliationService.class);

    static final String EVENT_TYPE = "EMERGENCY_DEFERRED_CHARGE";
    static final String AGGREGATE_TYPE = "EMERGENCY_DEFERRED_CHARGE";

    private final EmergencyDeferredChargeRepository repository;
    private final ServiceAccessDecisionRepository accessDecisionRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public EmergencyReconciliationService(EmergencyDeferredChargeRepository repository,
                                          ServiceAccessDecisionRepository accessDecisionRepository,
                                          EventOutboxRepository outboxRepository,
                                          ObjectMapper objectMapper) {
        this.repository = repository;
        this.accessDecisionRepository = accessDecisionRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Flag a charge accrued under EMERGENCY_OVERRIDE as deferred, pending reconciliation.
     *
     * <p><b>Invariant (M1): only emergency-overridden access can be reconciled.</b> This is
     * enforced on BOTH entry paths:
     * <ul>
     *   <li><b>With a gate decision</b> ({@code serviceAccessDecisionId} present): the referenced
     *       decision must have status {@code EMERGENCY_OVERRIDE}; any other status is rejected.</li>
     *   <li><b>Without a gate decision</b> (bedside emergency where the gate decision was not
     *       recorded in COSTA): an explicit emergency justification is required — both
     *       {@code reason} and {@code audit_reference} must be present. This path NEVER zeroes a
     *       charge; it only defers it to PENDING. Actual value disposal (incl. WAIVER) happens
     *       only at {@link #reconcile} via an explicit, audited settlement route. The hard gate
     *       prevents arbitrary deferral of a charge whose access was not emergency-overridden.</li>
     * </ul>
     *
     * <p><b>Idempotency (M2):</b> when a concrete {@code charge_id} is supplied, an existing
     * deferred row for that charge is returned instead of creating a second one — one charge
     * yields one deferred row / one reconciliation. A unique index backs this in the database.
     */
    @Transactional
    public EmergencyDeferredChargeResponse flag(FlagEmergencyDeferredChargeRequest req) {
        var ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();

        // M2: idempotent on the concrete charge.
        if (req.chargeId() != null && !req.chargeId().isBlank()) {
            var existing = repository.findByTenantIdAndChargeId(tenantId, req.chargeId());
            if (existing.isPresent()) {
                return EmergencyDeferredChargeResponse.fromEntity(existing.get());
            }
        }

        EmergencyDeferredChargeEntity e = new EmergencyDeferredChargeEntity();
        e.setTenantId(tenantId);
        e.setServiceAccessDecisionId(req.serviceAccessDecisionId());
        e.setChargeId(req.chargeId());
        e.setEncounterId(req.encounterId());
        e.setFacilityId(req.facilityId() != null ? req.facilityId() : ctx.facilityId());
        e.setProvisionalPersonRef(req.provisionalPersonRef());
        e.setDeferredAmount(req.deferredAmount());
        e.setCurrency(req.currency() != null && !req.currency().isBlank() ? req.currency() : "USD");
        e.setReconciliationReason(req.reason());
        e.setFlaggedBy(ctx.actorId());
        e.setAuditReference(req.auditReference());
        e.setReconciliationState(ReconciliationState.PENDING);

        // If a gate decision is referenced, inherit context and assert it was an emergency override.
        if (req.serviceAccessDecisionId() != null) {
            ServiceAccessDecisionEntity decision = accessDecisionRepository
                    .findByServiceAccessDecisionIdAndTenantId(req.serviceAccessDecisionId(), tenantId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Service access decision not found: " + req.serviceAccessDecisionId()));
            if (decision.getAccessStatus() != ServiceAccessStatus.EMERGENCY_OVERRIDE) {
                throw new IllegalArgumentException(
                        "Service access decision is not an EMERGENCY_OVERRIDE; only emergency-overridden "
                                + "access can be reconciled as a deferred charge");
            }
            if (e.getEncounterId() == null) {
                e.setEncounterId(decision.getEncounterId());
            }
            if (e.getFacilityId() == null) {
                e.setFacilityId(decision.getFacilityId());
            }
            if (e.getProvisionalPersonRef() == null) {
                e.setProvisionalPersonRef(decision.getPatientCpid());
            }
            if (e.getDeferredAmount() == null) {
                e.setDeferredAmount(decision.getTariffedPrice() != null
                        ? decision.getTariffedPrice() : decision.getEstimatedCost());
            }
        } else {
            // M1: no gate decision to prove the emergency override — require an explicit emergency
            // justification + audit so a charge whose access was NOT emergency-overridden can never
            // be silently deferred. (Decision: gate the free-form path hard rather than forbid it,
            // because bedside emergencies legitimately lack a COSTA-recorded gate decision.)
            if (req.reason() == null || req.reason().isBlank()
                    || req.auditReference() == null || req.auditReference().isBlank()) {
                throw new IllegalArgumentException(
                        "Emergency-deferred flag without a service_access_decision_id requires an explicit "
                                + "emergency justification: both 'reason' and 'audit_reference' are mandatory");
            }
        }

        e = repository.save(e);
        // Value-event: deferred:true, reconciliationState:PENDING (C8 contract).
        publishValueEvent(e, "EMERGENCY_OVERRIDE_INVOKED");
        log.info("Flagged emergency-deferred charge {} (encounter={}, amount={} {})",
                e.getDeferredChargeId(), e.getEncounterId(), e.getDeferredAmount(), e.getCurrency());
        return EmergencyDeferredChargeResponse.fromEntity(e);
    }

    @Transactional(readOnly = true)
    public List<EmergencyDeferredChargeResponse> list(String state, String provisionalPersonRef) {
        var ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        List<EmergencyDeferredChargeEntity> rows;
        if (provisionalPersonRef != null && !provisionalPersonRef.isBlank()) {
            rows = repository.findTop100ByTenantIdAndProvisionalPersonRefOrderByCreatedAtDesc(
                    tenantId, provisionalPersonRef.trim());
        } else if (state != null && !state.isBlank()) {
            ReconciliationState rs = parseEnum(ReconciliationState.class, state, "state");
            rows = repository.findTop100ByTenantIdAndReconciliationStateOrderByCreatedAtDesc(tenantId, rs);
        } else {
            rows = repository.findTop100ByTenantIdOrderByCreatedAtDesc(tenantId);
        }
        return rows.stream().map(EmergencyDeferredChargeResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public EmergencyDeferredChargeResponse require(UUID id) {
        return EmergencyDeferredChargeResponse.fromEntity(load(id));
    }

    /** Link the provisional emergency person to a confirmed person (audit chain preserved). */
    @Transactional
    public EmergencyDeferredChargeResponse linkConfirmedPerson(UUID id, LinkConfirmedPersonRequest req) {
        var ctx = TrustContextHolder.require();
        EmergencyDeferredChargeEntity e = load(id);
        if (e.getReconciliationState() == ReconciliationState.CANCELLED) {
            throw new IllegalArgumentException("Cannot link identity on a cancelled deferred charge");
        }
        e.setConfirmedPersonRef(req.confirmedPersonRef().trim());
        e.setIdentityReconciled(true);
        e.setIdentityReconciledAt(OffsetDateTime.now());
        if (req.reason() != null && !req.reason().isBlank()) {
            e.setReconciliationReason(req.reason());
        }
        e.setReconciledBy(ctx.actorId());
        e = repository.save(e);
        publishValueEvent(e, "IDENTITY_RECONCILED");
        log.info("Linked deferred charge {} provisional={} -> confirmed={}",
                e.getDeferredChargeId(), e.getProvisionalPersonRef(), e.getConfirmedPersonRef());
        return EmergencyDeferredChargeResponse.fromEntity(e);
    }

    /** Route a pending deferred charge to settle/claim/waiver and close reconciliation. */
    @Transactional
    public EmergencyDeferredChargeResponse reconcile(UUID id, ReconcileDeferredChargeRequest req) {
        var ctx = TrustContextHolder.require();
        EmergencyDeferredChargeEntity e = load(id);
        if (e.getReconciliationState() == ReconciliationState.RECONCILED) {
            throw new IllegalArgumentException("Deferred charge already reconciled: " + id);
        }
        if (e.getReconciliationState() == ReconciliationState.CANCELLED) {
            throw new IllegalArgumentException("Deferred charge is cancelled: " + id);
        }
        SettlementRoute route = parseEnum(SettlementRoute.class, req.settlementRoute(), "settlement_route");
        e.setSettlementRoute(route);
        e.setSettlementReference(req.settlementReference());
        if (req.reason() != null && !req.reason().isBlank()) {
            e.setReconciliationReason(req.reason());
        }
        e.setReconciliationState(ReconciliationState.RECONCILED);
        e.setReconciledBy(ctx.actorId());
        e.setReconciledAt(OffsetDateTime.now());
        e = repository.save(e);
        publishValueEvent(e, "RECONCILED");
        log.info("Reconciled deferred charge {} via {} (ref={})",
                e.getDeferredChargeId(), route, e.getSettlementReference());
        return EmergencyDeferredChargeResponse.fromEntity(e);
    }

    private EmergencyDeferredChargeEntity load(UUID id) {
        var ctx = TrustContextHolder.require();
        return repository.findByDeferredChargeIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Emergency deferred charge not found: " + id));
    }

    /**
     * Emit an {@code EMERGENCY_DEFERRED_CHARGE} value-event onto the outbox. The
     * {@link zw.gov.mohcc.impilo.costa.kafka.OutboxPublisher} dual-emits it to
     * {@code core.transaction.events} (C8) so Lane-C surfaces and patient notify can consume it.
     */
    /**
     * Enqueue the EMERGENCY_DEFERRED_CHARGE value-event in the SAME transaction as the flag/
     * link/reconcile state change (M3). An outbox-save failure must roll back the business
     * state so we never commit a deferred-charge state change (incl. WAIVER routing) with no
     * value event on core.transaction.events (silent value leak).
     */
    private void publishValueEvent(EmergencyDeferredChargeEntity e, String sourceServiceEvent) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deferredChargeId", e.getDeferredChargeId().toString());
        payload.put("kind", EVENT_TYPE);
        payload.put("sourceServiceEvent", sourceServiceEvent);
        payload.put("encounterId", e.getEncounterId());
        payload.put("serviceAccessDecisionId",
                e.getServiceAccessDecisionId() != null ? e.getServiceAccessDecisionId().toString() : null);
        payload.put("chargeId", e.getChargeId());
        payload.put("provisionalPersonRef", e.getProvisionalPersonRef());
        payload.put("confirmedPersonRef", e.getConfirmedPersonRef());
        payload.put("identityReconciled", e.isIdentityReconciled());
        payload.put("amount", e.getDeferredAmount());
        payload.put("currency", e.getCurrency());
        payload.put("payerKind", payerKind(e));
        payload.put("settlementRoute", e.getSettlementRoute() != null ? e.getSettlementRoute().name() : null);
        payload.put("settlementReference", e.getSettlementReference());
        payload.put("deferred", e.getReconciliationState() == ReconciliationState.PENDING);
        payload.put("reconciliationState", e.getReconciliationState().name());

        EventOutboxEntity ev = new EventOutboxEntity();
        ev.setAggregateType(AGGREGATE_TYPE);
        ev.setAggregateId(e.getDeferredChargeId().toString());
        ev.setEventType(EVENT_TYPE);
        try {
            ev.setPayload(objectMapper.writeValueAsString(payload));
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to serialize EMERGENCY_DEFERRED_CHARGE value-event", ex);
        }
        ev.setTenantId(e.getTenantId());
        outboxRepository.save(ev);
    }

    private static String payerKind(EmergencyDeferredChargeEntity e) {
        if (e.getSettlementRoute() == null) {
            return "DEFERRED";
        }
        return switch (e.getSettlementRoute()) {
            case SETTLE -> "SELF";
            case CLAIM -> "SCHEME";
            case WAIVER -> "WAIVER";
        };
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String field) {
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid " + field + ": " + raw);
        }
    }
}
