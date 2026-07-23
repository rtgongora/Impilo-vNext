package zw.gov.mohcc.impilo.telemonitoring.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * Review-cadence timer groundwork (OF-B21 side-effect (c), §14.2 capture item 6):
 * periodically sweeps ACTIVE plans and emits {@code telemonitoring.plan.review_due.v1}
 * when {@code now > last_review + cadence}. The sweep logic (and its de-duplication via
 * {@code review_due_notified_at}) lives in {@link MonitoringPlanService#sweepReviewsDue}
 * so tests can drive it with a controlled clock; this component is only the wall-clock
 * trigger, disabled under the {@code test} profile like the outbox publisher.
 *
 * <p>The event is a signal for the responsible team's worklist (and, in OF-B26/OF-B23,
 * PCT review tasks) — the sweep itself never mutates plan lifecycle state.</p>
 */
@Component
@Profile("!test")
public class PlanReviewScheduler {

    private static final Logger log = LoggerFactory.getLogger(PlanReviewScheduler.class);

    private final MonitoringPlanService planService;

    public PlanReviewScheduler(MonitoringPlanService planService) {
        this.planService = planService;
    }

    @Scheduled(fixedDelayString = "${telemonitoring.review-sweep.interval-ms:300000}")
    public void sweep() {
        int emitted = planService.sweepReviewsDue(OffsetDateTime.now());
        if (emitted > 0) {
            log.info("Review sweep emitted {} telemonitoring.plan.review_due.v1 events", emitted);
        }
    }
}
