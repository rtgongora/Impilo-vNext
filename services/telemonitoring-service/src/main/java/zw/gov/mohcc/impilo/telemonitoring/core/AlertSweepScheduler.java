package zw.gov.mohcc.impilo.telemonitoring.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * Wall-clock triggers for the OF-B26 sweeps (disabled under the {@code test} profile,
 * like the outbox publisher — tests drive the sweep logic with a controlled clock):
 *
 * <ul>
 *   <li><b>Device-offline heartbeat</b> (§14.6 trigger 8, journey #65) — assigned
 *       devices silent past the effective tolerance open ONE device-offline episode
 *       and raise the rung-4 CHW task.</li>
 *   <li><b>Overdue acknowledgement</b> (§14.6 escalation-on-silence) — unacknowledged
 *       amber+ episodes past their response window escalate automatically, audited.</li>
 * </ul>
 */
@Component
@Profile("!test")
public class AlertSweepScheduler {

    private static final Logger log = LoggerFactory.getLogger(AlertSweepScheduler.class);

    private final AlertEvaluationService evaluationService;

    public AlertSweepScheduler(AlertEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @Scheduled(fixedDelayString = "${telemonitoring.alert.offline-sweep-interval-ms:900000}")
    public void sweepDeviceOffline() {
        int opened = evaluationService.sweepDeviceOffline(OffsetDateTime.now());
        if (opened > 0) {
            log.info("Device-offline sweep opened {} alert episode(s)", opened);
        }
    }

    @Scheduled(fixedDelayString = "${telemonitoring.alert.overdue-sweep-interval-ms:60000}")
    public void sweepOverdue() {
        int escalated = evaluationService.sweepOverdueAcknowledgements(OffsetDateTime.now());
        if (escalated > 0) {
            log.info("Overdue-acknowledgement sweep auto-escalated {} episode(s)", escalated);
        }
    }
}
