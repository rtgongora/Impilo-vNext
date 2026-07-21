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

        // journeyId is intentionally null: this is a referral transition, not a journey,
        // and the telemetry journey_id column is ULID-sized (varchar 26).
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
