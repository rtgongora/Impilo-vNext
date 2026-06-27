package zw.gov.mohcc.impilo.madi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.madi.events.MadiEventEmitter;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodOrderSlaEntity;
import zw.gov.mohcc.impilo.madi.persistence.repository.BloodOrderSlaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Blood-order SLA timers (spec §3.D turnaround). Starts a timer when a stage begins, marks it MET
 * when the stage completes in time, and a scheduled scan marks overdue open timers BREACHED — each
 * emitting an {@code SLA_BREACHED} event for the dashboard / escalation.
 *
 * <p>Stages: {@code CROSSMATCH} (target from submit) and {@code ISSUE} (target from crossmatch).
 * Targets are configurable; defaults: crossmatch 120 min, issue 1440 min (24 h).</p>
 */
@Service
public class BloodOrderSlaService {

    public static final String STAGE_CROSSMATCH = "CROSSMATCH";
    public static final String STAGE_ISSUE = "ISSUE";

    private static final Logger log = LoggerFactory.getLogger(BloodOrderSlaService.class);

    private final BloodOrderSlaRepository repository;
    private final MadiEventEmitter eventEmitter;
    private final int crossmatchMinutes;
    private final int issueMinutes;

    public BloodOrderSlaService(BloodOrderSlaRepository repository,
                                MadiEventEmitter eventEmitter,
                                @Value("${madi.sla.crossmatch-minutes:120}") int crossmatchMinutes,
                                @Value("${madi.sla.issue-minutes:1440}") int issueMinutes) {
        this.repository = repository;
        this.eventEmitter = eventEmitter;
        this.crossmatchMinutes = crossmatchMinutes;
        this.issueMinutes = issueMinutes;
    }

    /** Default target minutes for a stage. */
    public int defaultMinutes(String stage) {
        return STAGE_CROSSMATCH.equals(stage) ? crossmatchMinutes : issueMinutes;
    }

    /** Start (or restart) an OPEN timer for a stage. Idempotent per (order, stage) open timer. */
    @Transactional
    public BloodOrderSlaEntity start(UUID tenantId, UUID orderId, String stage) {
        if (repository.findFirstByOrderIdAndStageAndStatus(orderId, stage, "OPEN").isPresent()) {
            return repository.findFirstByOrderIdAndStageAndStatus(orderId, stage, "OPEN").get();
        }
        BloodOrderSlaEntity sla = new BloodOrderSlaEntity();
        sla.setTenantId(tenantId);
        sla.setOrderId(orderId);
        sla.setStage(stage);
        sla.setStartedAt(OffsetDateTime.now());
        sla.setTargetAt(OffsetDateTime.now().plusMinutes(defaultMinutes(stage)));
        sla.setStatus("OPEN");
        BloodOrderSlaEntity saved = repository.save(sla);
        log.info("Blood SLA started: orderId={}, stage={}, targetAt={}", orderId, stage, saved.getTargetAt());
        return saved;
    }

    /** Mark the open timer for a stage MET (completed within / at the target). */
    @Transactional
    public void complete(UUID orderId, String stage) {
        repository.findFirstByOrderIdAndStageAndStatus(orderId, stage, "OPEN").ifPresent(sla -> {
            sla.setStatus("MET");
            sla.setCompletedAt(OffsetDateTime.now());
            repository.save(sla);
            log.info("Blood SLA met: orderId={}, stage={}", orderId, stage);
        });
    }

    /** Scheduled breach detector: mark overdue OPEN timers BREACHED and emit an event each. */
    @Scheduled(fixedDelayString = "${madi.sla.scan-interval-ms:60000}")
    @Transactional
    public int scanForBreaches() {
        List<BloodOrderSlaEntity> overdue = repository.findByStatusAndTargetAtBefore("OPEN", OffsetDateTime.now());
        for (BloodOrderSlaEntity sla : overdue) {
            sla.setStatus("BREACHED");
            sla.setBreachedAt(OffsetDateTime.now());
            repository.save(sla);
            eventEmitter.emit("BLOOD_ORDER", sla.getOrderId().toString(), "SLA_BREACHED", "BLOOD_ORDER",
                    sla.getOrderId().toString(),
                    Map.of("stage", sla.getStage(), "targetAt", sla.getTargetAt().toString()),
                    sla.getTenantId());
        }
        if (!overdue.isEmpty()) {
            log.warn("Blood SLA breaches detected: {}", overdue.size());
        }
        return overdue.size();
    }

    /** Count of currently-breached SLAs for a tenant (dashboard surfacing). */
    @Transactional(readOnly = true)
    public long breachedCount(UUID tenantId) {
        return repository.countByTenantIdAndStatus(tenantId, "BREACHED");
    }
}
