package zw.gov.mohcc.impilo.telemonitoring.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Bounded retry sweep for the single SHR writer (OF-B25 honesty law): PENDING/RETRYING
 * readings are re-attempted until success or the attempt cap parks them EXHAUSTED —
 * a failed clinical write is visible and recoverable, never silently absorbed
 * (§14.5 invariant). Disabled under {@code test} (the writer's {@code retryPending()}
 * is exercised directly).
 */
@Component
@Profile("!test")
public class ObservationWriteRetrySweep {

    private static final Logger log = LoggerFactory.getLogger(ObservationWriteRetrySweep.class);

    private final ObservationShrWriter shrWriter;

    public ObservationWriteRetrySweep(ObservationShrWriter shrWriter) {
        this.shrWriter = shrWriter;
    }

    @Scheduled(fixedDelayString = "${telemonitoring.shr.retry-sweep-interval-ms:60000}")
    public void sweep() {
        int attempted = shrWriter.retryPending();
        if (attempted > 0) {
            log.info("SHR write retry sweep attempted {} reading(s)", attempted);
        }
    }
}
