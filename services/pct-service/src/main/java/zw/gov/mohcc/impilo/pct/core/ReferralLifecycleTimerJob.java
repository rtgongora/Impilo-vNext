package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.domain.ReferralStatus;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ReferralEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ReferralRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Teleconsult no-show / expiry lifecycle timer (TM-B2 / TM-B4).
 *
 * <p>The referral state machine declares {@link ReferralStatus#EXPIRED} and
 * {@link ReferralStatus#ABANDONED} as legal targets but, until this job, nothing
 * ever set them — a teleconsult referral could hang forever awaiting acceptance
 * (or awaiting a patient who never joined a scheduled session). Two sweeps close
 * that gap:</p>
 * <ul>
 *   <li><b>Expiry (TM-B2)</b>: referrals still awaiting acceptance (pre-accept
 *       statuses {@code SUBMITTED} / {@code ROUTED}) whose awaiting-since instant
 *       (submittedAt / routedAt, else createdAt) is older than
 *       {@code expiry-minutes} → {@code EXPIRED} via the {@code "expire"} edge.</li>
 *   <li><b>No-show (TM-B4)</b>: {@code SCHEDULED} teleconsults whose scheduled
 *       start is more than {@code no-show-minutes} in the past and which never
 *       progressed → {@code ABANDONED} via the {@code "abandon"} edge.</li>
 * </ul>
 *
 * <p>System-initiated: runs with no TrustContext. Transitions flow through the
 * TrustContext-free {@link ReferralStateMachine#apply} (which resolves the actor
 * leniently to {@code "system"} and records the ledger in an independent
 * REQUIRES_NEW transaction, so an audit failure can never roll back the clinical
 * mutation). The business outbox event is written directly here — the two-arg
 * {@code emitOutbox} on the orchestration service calls {@code TrustContextHolder.require()}
 * and must not be used from a timer.</p>
 *
 * <p>Each row is transitioned in its own {@code REQUIRES_NEW} transaction and the
 * sweep loop try/catches every row, so one poisoned row (e.g. an optimistic
 * {@code STALE_WRITE} from a concurrent provider action) never aborts the batch.</p>
 */
@Component
public class ReferralLifecycleTimerJob {

    public static final String SYSTEM_ACTOR = "system:lifecycle-monitor";

    /** Pre-accept statuses swept for expiry (both legally transition to EXPIRED). */
    private static final List<String> PRE_ACCEPT_STATUSES =
            List.of(ReferralStatus.SUBMITTED.name(), ReferralStatus.ROUTED.name());

    private static final Logger log = LoggerFactory.getLogger(ReferralLifecycleTimerJob.class);

    private final ReferralRepository referralRepository;
    private final ReferralStateMachine referralStateMachine;
    private final VirtualPoolQueueService virtualPoolQueueService;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    /** Self-reference so per-row @Transactional(REQUIRES_NEW) methods go through the proxy. */
    private final ObjectProvider<ReferralLifecycleTimerJob> self;

    @Value("${pct.telemedicine.lifecycle.enabled:true}")
    private boolean enabled;

    @Value("${pct.telemedicine.lifecycle.expiry-minutes:1440}")
    private long expiryMinutes;

    @Value("${pct.telemedicine.lifecycle.no-show-minutes:120}")
    private long noShowMinutes;

    public ReferralLifecycleTimerJob(ReferralRepository referralRepository,
                                     ReferralStateMachine referralStateMachine,
                                     VirtualPoolQueueService virtualPoolQueueService,
                                     EventOutboxRepository outboxRepository,
                                     ObjectMapper objectMapper,
                                     ObjectProvider<ReferralLifecycleTimerJob> self) {
        this.referralRepository = referralRepository;
        this.referralStateMachine = referralStateMachine;
        this.virtualPoolQueueService = virtualPoolQueueService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.self = self;
    }

    @Scheduled(fixedDelayString = "${pct.telemedicine.lifecycle.sweep-interval-ms:60000}",
            initialDelayString = "${pct.telemedicine.lifecycle.sweep-initial-delay-ms:45000}")
    public void sweep() {
        if (!enabled) {
            return;
        }
        int expired = expireStaleReferrals();
        int abandoned = abandonNoShowReferrals();
        if (expired > 0 || abandoned > 0) {
            log.info("Referral lifecycle sweep: expired {}, abandoned {}", expired, abandoned);
        }
    }

    // ---- expiry sweep (TM-B2): SUBMITTED/ROUTED awaiting acceptance ----

    /** Expire every pre-accept referral older than the expiry threshold. Returns the count expired. */
    public int expireStaleReferrals() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(expiryMinutes);
        List<ReferralEntity> candidates =
                referralRepository.findTop100ByStatusInOrderByCreatedAtAsc(PRE_ACCEPT_STATUSES);
        int expired = 0;
        for (ReferralEntity candidate : candidates) {
            if (!awaitingSince(candidate).isBefore(cutoff)) {
                continue; // under threshold — leave it awaiting
            }
            try {
                if (self().expireReferral(candidate.getReferralId())) {
                    expired++;
                }
            } catch (Exception e) {
                log.warn("Referral expiry sweep skipped referral {} ({})",
                        candidate.getReferralId(), e.getMessage());
            }
        }
        return expired;
    }

    /**
     * Transition one referral to EXPIRED in an independent transaction. Reloads and
     * re-checks the status so a referral accepted between the sweep read and now is
     * left alone. Retires (dequeues) any live pool worklist item so the pool and the
     * case state don't diverge.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expireReferral(UUID referralId) {
        ReferralEntity entity = referralRepository.findByReferralId(referralId).orElse(null);
        if (entity == null) {
            return false;
        }
        String from = entity.getStatus();
        if (!PRE_ACCEPT_STATUSES.contains(from)) {
            return false; // raced with an accept/decline/cancel
        }
        referralStateMachine.apply(entity, ReferralStatus.EXPIRED, "expire");
        ReferralEntity saved = referralRepository.save(entity);
        // Retire the pool worklist item so the provider queue and case state agree.
        try {
            virtualPoolQueueService.completeForReferral(saved, SYSTEM_ACTOR);
        } catch (Exception e) {
            log.warn("Could not dequeue pool item(s) for expired referral {}: {}",
                    referralId, e.getMessage());
        }
        emitLifecycleEvent("telemedicine.session.expired", saved, from,
                "Expired after " + expiryMinutes + " min awaiting acceptance");
        log.info("Referral {} expired ({} -> EXPIRED) after {} min awaiting acceptance",
                referralId, from, expiryMinutes);
        return true;
    }

    // ---- no-show sweep (TM-B4): SCHEDULED sessions the patient never joined ----

    /** Abandon every SCHEDULED referral whose start is past the no-show cutoff. Returns the count. */
    public int abandonNoShowReferrals() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(noShowMinutes);
        List<ReferralEntity> candidates = referralRepository
                .findTop100ByStatusAndScheduledAtBeforeOrderByScheduledAtAsc(
                        ReferralStatus.SCHEDULED.name(), cutoff);
        int abandoned = 0;
        for (ReferralEntity candidate : candidates) {
            try {
                if (self().abandonReferral(candidate.getReferralId())) {
                    abandoned++;
                }
            } catch (Exception e) {
                log.warn("Referral no-show sweep skipped referral {} ({})",
                        candidate.getReferralId(), e.getMessage());
            }
        }
        return abandoned;
    }

    /** Transition one SCHEDULED referral to ABANDONED in an independent transaction. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean abandonReferral(UUID referralId) {
        ReferralEntity entity = referralRepository.findByReferralId(referralId).orElse(null);
        if (entity == null) {
            return false;
        }
        String from = entity.getStatus();
        if (!ReferralStatus.SCHEDULED.name().equals(from)) {
            return false; // raced with a join/complete/cancel
        }
        referralStateMachine.apply(entity, ReferralStatus.ABANDONED, "abandon");
        ReferralEntity saved = referralRepository.save(entity);
        emitLifecycleEvent("telemedicine.session.abandoned", saved, from,
                "Abandoned: no-show " + noShowMinutes + " min past scheduled start");
        log.info("Referral {} abandoned ({} -> ABANDONED) — no-show {} min past scheduled start",
                referralId, from, noShowMinutes);
        return true;
    }

    // ---- helpers ----

    /** Awaiting-since instant per pre-accept status: submittedAt / routedAt, else createdAt. */
    private OffsetDateTime awaitingSince(ReferralEntity r) {
        Optional<ReferralStatus> status = ReferralStatus.fromLenient(r.getStatus());
        OffsetDateTime ts = null;
        if (status.isPresent()) {
            if (status.get() == ReferralStatus.SUBMITTED) {
                ts = r.getSubmittedAt();
            } else if (status.get() == ReferralStatus.ROUTED) {
                ts = r.getRoutedAt();
            }
        }
        return ts != null ? ts : r.getCreatedAt();
    }

    /** Direct (TrustContext-free) outbox write — tenant comes from the entity, not a TrustContext. */
    private void emitLifecycleEvent(String eventType, ReferralEntity referral,
                                    String previousStatus, String reason) {
        // TM-B20: version the timer-emitted lifecycle events (expired/abandoned) at the choke point.
        String versionedType = zw.gov.mohcc.impilo.pct.core.telemedicine.TelemedicineEventVersion.withV1(eventType);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", versionedType);
        payload.put("tenantId", referral.getTenantId().toString());
        payload.put("referralId", referral.getReferralId().toString());
        payload.put("patientCpid", referral.getPatientCpid());
        payload.put("previousStatus", previousStatus);
        payload.put("newStatus", referral.getStatus());
        payload.put("reason", reason);
        payload.put("actor", SYSTEM_ACTOR);
        payload.put("occurredAt", OffsetDateTime.now().toString());

        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("telemedicine");
        outbox.setAggregateId(referral.getReferralId().toString());
        outbox.setEventType(versionedType);
        outbox.setTenantId(referral.getTenantId());
        try {
            outbox.setPayload(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            outbox.setPayload("{}");
        }
        outboxRepository.save(outbox);
    }

    /** Proxy self for REQUIRES_NEW per-row isolation; falls back to {@code this} in unit tests. */
    private ReferralLifecycleTimerJob self() {
        ReferralLifecycleTimerJob proxy = self != null ? self.getIfAvailable() : null;
        return proxy != null ? proxy : this;
    }

    // Test seams for thresholds (no Spring context in unit tests).
    void setThresholds(long expiryMinutes, long noShowMinutes) {
        this.expiryMinutes = expiryMinutes;
        this.noShowMinutes = noShowMinutes;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
