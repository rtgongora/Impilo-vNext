package zw.gov.mohcc.impilo.experience.trust.shadow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Publishes the observer's own health, so the evidence report can state what it missed.
 *
 * <h2>Why a log line and not an endpoint</h2>
 * <p>An HTTP endpoint for this would itself be gated by ext_authz — the very mechanism under
 * measurement, and the one currently unable to match a rule for a browser user. Reading the
 * observer's coverage would then depend on the thing whose brokenness the observer exists to
 * quantify. A log line has no such dependency and is readable with {@code kubectl logs}.</p>
 *
 * <p>The counters that matter are the negative ones. {@code droppedQueueFull} and {@code
 * transportErrors} are requests the dataset does not contain, and they are not randomly
 * distributed — they cluster exactly when the estate is busiest, which is when the interesting
 * denials would happen. A report quoting only what was recorded would overstate its own
 * completeness.</p>
 */
@Component
public class ShadowObserverHealthReporter {

    private static final Logger log = LoggerFactory.getLogger(ShadowObserverHealthReporter.class);

    private final ShadowObservationProperties properties;
    private final ShadowProbeDispatcher dispatcher;

    public ShadowObserverHealthReporter(ShadowObservationProperties properties,
                                        ShadowProbeDispatcher dispatcher) {
        this.properties = properties;
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${impilo.trust.shadow-bearer.report-interval-ms:60000}")
    public void report() {
        if (!properties.isEnabled()) {
            return;
        }
        ShadowProbeDispatcher.Snapshot s = dispatcher.snapshot();

        log.info("impilo.trust.shadow_observer eligible={} skipped_no_bearer={} "
                        + "skipped_no_verdict={} dispatched={} dropped_queue_full={} "
                        + "transport_errors={} canary_completed={} canary_timeouts={} "
                        + "async_queue_depth={} canary_queue_depth={} "
                        + "canary_samples={} canary_p50_ms={} canary_p95_ms={} canary_p99_ms={}",
                s.eligible(), s.skippedNoBearer(), s.skippedNoVerdict(),
                s.dispatched(), s.droppedQueueFull(), s.transportErrors(),
                s.canaryCompleted(), s.canaryTimeouts(),
                s.asyncQueueDepth(), s.canaryQueueDepth(),
                s.canarySamples(), s.canaryP50(), s.canaryP95(), s.canaryP99());
    }
}
