package zw.gov.mohcc.impilo.pct.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.ReferralTransitionEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.ReferralTransitionRepository;

import java.util.Map;
import java.util.UUID;

/**
 * Persists the referral transition ledger + telemetry in an INDEPENDENT transaction
 * (TM-B1). This is deliberately a separate bean so the write runs with
 * {@link Propagation#REQUIRES_NEW}: an audit failure rolls back only its own
 * transaction and can never poison — or roll back — the clinical mutation that
 * triggered it. Protecting the live spine is the whole point of shadow mode.
 */
@Service
public class ReferralTransitionRecorder {

    private static final Logger log = LoggerFactory.getLogger(ReferralTransitionRecorder.class);

    private final ReferralTransitionRepository transitionRepository;
    private final TelemetryService telemetryService;

    public ReferralTransitionRecorder(ReferralTransitionRepository transitionRepository,
                                      TelemetryService telemetryService) {
        this.transitionRepository = transitionRepository;
        this.telemetryService = telemetryService;
    }

    /**
     * Persist the transition ledger row in an independent transaction. Telemetry is emitted
     * SEPARATELY (see {@link #recordTransitionTelemetry}) — deliberately not in this transaction —
     * so a telemetry failure can never roll back the durable audit row. TelemetryService.record()
     * calls {@code TrustContextHolder.require()}, which throws on threads that never passed through
     * a request (the ReferralLifecycleTimerJob expire/abandon sweeps run on the scheduler with no
     * TrustContext); because that telemetry method is {@code @Transactional} it marks the shared
     * transaction rollback-only on the way out, which — if the two shared this transaction — would
     * silently drop the ledger row for every timer-driven transition (caught by
     * teleconsult-hardening-journeys.sh J-TH-4).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID referralId, UUID tenantId, String fromStatus, String toStatus,
                       String action, String actor, boolean allowed, String mode) {
        ReferralTransitionEntity row = new ReferralTransitionEntity();
        row.setReferralId(referralId);
        row.setTenantId(tenantId);
        row.setFromStatus(fromStatus);
        row.setToStatus(toStatus);
        row.setAction(action);
        row.setActor(actor);
        row.setAllowed(allowed);
        row.setMode(mode);
        transitionRepository.save(row);
    }

    /**
     * Best-effort transition telemetry in its OWN independent transaction, so a missing
     * TrustContext on timer/consumer threads rolls back only this (empty) transaction and never
     * the ledger row. journeyId is intentionally null: this is a referral transition, not a
     * journey, and the telemetry journey_id column is ULID-sized (varchar 26).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordTransitionTelemetry(String fromStatus, String toStatus, String action,
                                          boolean allowed, String mode) {
        telemetryService.record("telemedicine.referral.transition", null,
                Map.<String, Object>of(
                        "from", fromStatus == null ? "" : fromStatus,
                        "to", toStatus,
                        "action", action,
                        "allowed", Boolean.toString(allowed),
                        "mode", mode));
    }

    /**
     * Record a safety/authority gate decision (TM-B8) in an independent transaction —
     * the shadow signal for the consent-on-submit and authority-on-accept gates.
     * journeyId is null (see above).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordGate(String gate, boolean satisfied, String mode, String detail) {
        telemetryService.record("telemedicine.gate." + gate, null,
                Map.<String, Object>of(
                        "gate", gate,
                        "satisfied", Boolean.toString(satisfied),
                        "mode", mode,
                        "detail", detail == null ? "" : detail));
    }
}
