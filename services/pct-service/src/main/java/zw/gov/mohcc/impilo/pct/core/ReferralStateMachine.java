package zw.gov.mohcc.impilo.pct.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.pct.core.telemedicine.ReferralTransitionGuardProperties;
import zw.gov.mohcc.impilo.pct.domain.ReferralStatus;
import zw.gov.mohcc.impilo.pct.persistence.entity.ReferralEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ReferralTransitionEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.ReferralTransitionRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Guarded transition dispatcher for the telemedicine referral lifecycle (TM-B1).
 *
 * <p>Deliberately "dispatcher-lite": {@link #apply} validates the transition,
 * records the ledger + telemetry, and sets the entity status — but does NOT save
 * the entity and does NOT emit the business outbox event. Each orchestration
 * mutator keeps its own {@code save(...)} + {@code emitOutbox(...)} tail, so the
 * rich per-action side effects (queue enqueue/dequeue, COSTA value trigger,
 * distinct event names, completion-note validation) stay exactly where they are.</p>
 *
 * <p>The guard is TrustContext-free (resolves actor/tenant leniently) so timer
 * jobs and Kafka consumers can drive transitions too. Rollout is shadow-then-enforce
 * via {@link ReferralTransitionGuardProperties}.</p>
 */
@Service
public class ReferralStateMachine {

    private static final Logger log = LoggerFactory.getLogger(ReferralStateMachine.class);

    /**
     * Allowed transition graph (National Telemedicine &amp; Virtual Care Specification §11.3).
     * Note the documented compatibility edges beyond §11.3 needed by the runtime-proven
     * live flows (e.g. {@code ACCEPTED -> COMPLETED}, the J-VC-1 accept→complete path;
     * {@code SCHEDULED -> RESPONDED/COMPLETED}, the direct-patient scheduled path).
     */
    private static final Map<ReferralStatus, Set<ReferralStatus>> TRANSITIONS;

    static {
        Map<ReferralStatus, Set<ReferralStatus>> m = new EnumMap<>(ReferralStatus.class);

        m.put(ReferralStatus.DRAFT, EnumSet.of(
                ReferralStatus.SUBMITTED, ReferralStatus.ROUTED,
                ReferralStatus.CANCELLED, ReferralStatus.EXPIRED, ReferralStatus.ENTERED_IN_ERROR));
        m.put(ReferralStatus.ROUTED, EnumSet.of(
                ReferralStatus.SUBMITTED, ReferralStatus.CANCELLED, ReferralStatus.EXPIRED));
        m.put(ReferralStatus.SUBMITTED, EnumSet.of(
                ReferralStatus.ACCEPTED, ReferralStatus.DECLINED, ReferralStatus.OFFERED,
                ReferralStatus.NEEDS_MORE_INFORMATION, ReferralStatus.SCHEDULED,
                ReferralStatus.ESCALATED, ReferralStatus.EXPIRED, ReferralStatus.CANCELLED));
        m.put(ReferralStatus.OFFERED, EnumSet.of(
                ReferralStatus.ACCEPTED, ReferralStatus.SUBMITTED, ReferralStatus.EXPIRED));
        m.put(ReferralStatus.NEEDS_MORE_INFORMATION, EnumSet.of(
                ReferralStatus.SUBMITTED, ReferralStatus.CANCELLED, ReferralStatus.EXPIRED));
        m.put(ReferralStatus.ACCEPTED, EnumSet.of(
                ReferralStatus.RESPONDED, ReferralStatus.IN_REVIEW, ReferralStatus.SCHEDULED,
                ReferralStatus.DECLINED, ReferralStatus.ESCALATED, ReferralStatus.TRANSFERRED,
                ReferralStatus.COMPLETED)); // COMPLETED: J-VC-1 compat edge
        m.put(ReferralStatus.SCHEDULED, EnumSet.of(
                ReferralStatus.ACCEPTED, ReferralStatus.IN_REVIEW, ReferralStatus.RESPONDED,
                ReferralStatus.CANCELLED, ReferralStatus.ABANDONED,
                ReferralStatus.COMPLETED)); // compat: scheduled direct-patient consult
        m.put(ReferralStatus.IN_REVIEW, EnumSet.of(
                ReferralStatus.RESPONDED, ReferralStatus.ACCEPTED,
                ReferralStatus.DECLINED, ReferralStatus.ESCALATED));
        m.put(ReferralStatus.RESPONDED, EnumSet.of(
                ReferralStatus.COMPLETED, ReferralStatus.AWAITING_LOCAL_ACTION,
                ReferralStatus.AWAITING_PATIENT_ACTION, ReferralStatus.AWAITING_RESULTS,
                ReferralStatus.FOLLOW_UP_DUE, ReferralStatus.ESCALATED, ReferralStatus.TRANSFERRED));
        m.put(ReferralStatus.AWAITING_LOCAL_ACTION, EnumSet.of(
                ReferralStatus.FOLLOW_UP_DUE, ReferralStatus.COMPLETED_AWAITING_CLOSURE,
                ReferralStatus.COMPLETED, ReferralStatus.ESCALATED));
        m.put(ReferralStatus.AWAITING_PATIENT_ACTION, EnumSet.of(
                ReferralStatus.FOLLOW_UP_DUE, ReferralStatus.COMPLETED_AWAITING_CLOSURE,
                ReferralStatus.COMPLETED, ReferralStatus.ABANDONED, ReferralStatus.ESCALATED));
        m.put(ReferralStatus.AWAITING_RESULTS, EnumSet.of(
                ReferralStatus.FOLLOW_UP_DUE, ReferralStatus.COMPLETED_AWAITING_CLOSURE,
                ReferralStatus.COMPLETED, ReferralStatus.ESCALATED));
        m.put(ReferralStatus.FOLLOW_UP_DUE, EnumSet.of(
                ReferralStatus.COMPLETED_AWAITING_CLOSURE, ReferralStatus.COMPLETED,
                ReferralStatus.AWAITING_PATIENT_ACTION, ReferralStatus.ESCALATED));
        m.put(ReferralStatus.COMPLETED_AWAITING_CLOSURE, EnumSet.of(
                ReferralStatus.CLOSED, ReferralStatus.COMPLETED));
        m.put(ReferralStatus.COMPLETED, EnumSet.of(
                ReferralStatus.CLOSED, ReferralStatus.REOPENED));
        m.put(ReferralStatus.CLOSED, EnumSet.of(ReferralStatus.REOPENED));
        m.put(ReferralStatus.REOPENED, EnumSet.of(
                ReferralStatus.ACCEPTED, ReferralStatus.RESPONDED, ReferralStatus.SUBMITTED));
        m.put(ReferralStatus.DECLINED, EnumSet.of(
                ReferralStatus.SUBMITTED, ReferralStatus.CANCELLED));
        m.put(ReferralStatus.ESCALATED, EnumSet.of(
                ReferralStatus.TRANSFERRED, ReferralStatus.ACCEPTED,
                ReferralStatus.CLOSED, ReferralStatus.COMPLETED));
        m.put(ReferralStatus.TRANSFERRED, EnumSet.of(
                ReferralStatus.CLOSED, ReferralStatus.COMPLETED));
        m.put(ReferralStatus.EXPIRED, EnumSet.of(
                ReferralStatus.SUBMITTED, ReferralStatus.CLOSED));
        m.put(ReferralStatus.ABANDONED, EnumSet.of(
                ReferralStatus.SUBMITTED, ReferralStatus.CLOSED));
        // Terminal states
        m.put(ReferralStatus.CANCELLED, EnumSet.noneOf(ReferralStatus.class));
        m.put(ReferralStatus.ENTERED_IN_ERROR, EnumSet.noneOf(ReferralStatus.class));

        TRANSITIONS = m;
    }

    private final ReferralTransitionRepository transitionRepository;
    private final TelemetryService telemetryService;
    private final ReferralTransitionGuardProperties properties;

    public ReferralStateMachine(ReferralTransitionRepository transitionRepository,
                                TelemetryService telemetryService,
                                ReferralTransitionGuardProperties properties) {
        this.transitionRepository = transitionRepository;
        this.telemetryService = telemetryService;
        this.properties = properties;
    }

    /**
     * Apply a transition to {@code target} via {@code action}. Validates, records the
     * ledger + telemetry, and sets the entity status. Does not save or emit outbox.
     *
     * @throws PctDomainException {@code ILLEGAL_TRANSITION} (409) only in ENFORCE mode
     *                            when the transition is not permitted.
     */
    public void apply(ReferralEntity entity, ReferralStatus target, String action) {
        ReferralTransitionGuardProperties.Mode mode = properties.getMode();
        if (mode == ReferralTransitionGuardProperties.Mode.OFF) {
            entity.setStatus(target.name());
            return;
        }

        Optional<ReferralStatus> current = ReferralStatus.fromLenient(entity.getStatus());
        boolean allowed = isAllowed(current, target);

        record(entity, current.map(Enum::name).orElse(entity.getStatus()), target, action, allowed, mode);

        if (!allowed) {
            log.warn("Referral transition VIOLATION [{}]: {} -> {} (action={}, referral={})",
                    mode, current.map(Enum::name).orElse("<" + entity.getStatus() + ">"),
                    target, action, entity.getReferralId());
            if (mode == ReferralTransitionGuardProperties.Mode.ENFORCE) {
                throw new PctDomainException("ILLEGAL_TRANSITION", 409,
                        "Referral cannot transition from " + entity.getStatus() + " to " + target.name()
                                + " (action=" + action + ").");
            }
        }
        entity.setStatus(target.name());
    }

    /**
     * Apply a transition where the target arrives as a raw string (the
     * {@code updateReferralStage} status passthrough backdoor). An unresolvable
     * target is tolerated (recorded, set raw) rather than rejected — legacy BFF
     * flows must not break; the ledger surfaces what actually flows through here.
     */
    public void applyRaw(ReferralEntity entity, String rawTarget, String action) {
        Optional<ReferralStatus> target = ReferralStatus.fromLenient(rawTarget);
        if (target.isPresent()) {
            apply(entity, target.get(), action);
            return;
        }
        // Unknown target string: record leniently and set as-is.
        record(entity, entity.getStatus(), rawTarget, action, true,
                properties.getMode());
        log.info("Referral raw status passthrough (unknown target '{}', action={}, referral={})",
                rawTarget, action, entity.getReferralId());
        entity.setStatus(rawTarget == null ? null : rawTarget.trim().toUpperCase(Locale.ROOT));
    }

    /** Whether a transition is permitted (self-transition and lenient-unknown source are allowed). */
    public boolean isAllowed(Optional<ReferralStatus> current, ReferralStatus target) {
        if (current.isEmpty()) {
            return true; // legacy/unknown source status resolves leniently
        }
        ReferralStatus from = current.get();
        if (from == target) {
            return true; // self-transition no-op (MESSAGE respond, idempotent replays)
        }
        if (TRANSITIONS.getOrDefault(from, EnumSet.noneOf(ReferralStatus.class)).contains(target)) {
            return true;
        }
        return properties.getAllowExtra().contains(from.name() + "->" + target.name());
    }

    /** Allowed target set for a status — powers a future {@code allowed-actions} surface. */
    public Set<ReferralStatus> allowedTargets(ReferralStatus from) {
        return TRANSITIONS.getOrDefault(from, EnumSet.noneOf(ReferralStatus.class));
    }

    private void record(ReferralEntity entity, String fromStatus, ReferralStatus target,
                        String action, boolean allowed, ReferralTransitionGuardProperties.Mode mode) {
        recordRaw(entity, fromStatus, target.name(), action, allowed, mode);
    }

    private void record(ReferralEntity entity, String fromStatus, String rawTarget,
                        String action, boolean allowed, ReferralTransitionGuardProperties.Mode mode) {
        recordRaw(entity, fromStatus, rawTarget, action, allowed, mode);
    }

    private void recordRaw(ReferralEntity entity, String fromStatus, String toStatus,
                           String action, boolean allowed, ReferralTransitionGuardProperties.Mode mode) {
        try {
            ReferralTransitionEntity row = new ReferralTransitionEntity();
            row.setReferralId(entity.getReferralId());
            row.setTenantId(entity.getTenantId());
            row.setFromStatus(fromStatus);
            row.setToStatus(toStatus);
            row.setAction(action);
            row.setActor(resolveActor());
            row.setAllowed(allowed);
            row.setMode(mode.name());
            transitionRepository.save(row);
            telemetryService.record("telemedicine.referral.transition",
                    entity.getReferralId() == null ? null : entity.getReferralId().toString(),
                    Map.<String, Object>of("from", fromStatus == null ? "" : fromStatus,
                            "to", toStatus, "action", action,
                            "allowed", Boolean.toString(allowed), "mode", mode.name()));
        } catch (Exception e) {
            // The ledger must never break a clinical mutation — record failures are logged only.
            log.warn("Failed to record referral transition ledger row: {}", e.getMessage());
        }
    }

    private String resolveActor() {
        TrustContext ctx = TrustContextHolder.get();
        if (ctx != null && ctx.actorId() != null) {
            return ctx.actorId();
        }
        return "system";
    }
}
