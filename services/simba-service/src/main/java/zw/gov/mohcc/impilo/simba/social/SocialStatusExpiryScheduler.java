package zw.gov.mohcc.impilo.simba.social;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.simba.social.core.SocialStatusService;

/**
 * Sweeps expired wellness-social statuses to HIDDEN on a fixed delay. Modeled on
 * {@code PreventiveReminderScheduler} / {@code SimbaOutboxPublisher}: disabled under the {@code test}
 * profile (tests call {@code expireOverdue} explicitly), toggle-able via
 * {@code simba.social.status-sweeper-enabled}.
 */
@Component
@Profile("!test")
@ConditionalOnProperty(name = "simba.social.status-sweeper-enabled", havingValue = "true", matchIfMissing = true)
public class SocialStatusExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(SocialStatusExpiryScheduler.class);

    private final SocialStatusService statusService;

    public SocialStatusExpiryScheduler(SocialStatusService statusService) {
        this.statusService = statusService;
    }

    @Scheduled(fixedDelayString = "${simba.social.status-sweep-interval-ms:120000}")
    public void sweepExpiredStatuses() {
        try {
            int hidden = statusService.expireOverdue(200);
            if (hidden > 0) {
                log.info("Hid {} expired wellness-social statuses", hidden);
            }
        } catch (RuntimeException ex) {
            log.warn("Social status expiry sweep failed: {}", ex.getMessage());
        }
    }
}
