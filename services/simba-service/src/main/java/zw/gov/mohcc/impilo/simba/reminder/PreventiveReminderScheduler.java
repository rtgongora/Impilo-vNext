package zw.gov.mohcc.impilo.simba.reminder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.simba.core.PreventiveReminderService;

/**
 * Drains due preventive reminders on a fixed delay — the Khuluma delivery seam. Modeled on
 * {@code SimbaOutboxPublisher}: disabled under the {@code test} profile (deterministic tests fire
 * reminders explicitly), and toggle-able via {@code simba.reminders.scheduler-enabled}.
 */
@Component
@Profile("!test")
@ConditionalOnProperty(name = "simba.reminders.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class PreventiveReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(PreventiveReminderScheduler.class);

    private final PreventiveReminderService reminderService;

    public PreventiveReminderScheduler(PreventiveReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Scheduled(fixedDelayString = "${simba.reminders.poll-interval-ms:60000}")
    public void fireDueReminders() {
        try {
            int fired = reminderService.fireDue(100);
            if (fired > 0) {
                log.info("Fired {} due wellness reminders", fired);
            }
        } catch (RuntimeException ex) {
            log.warn("Preventive reminder firing failed: {}", ex.getMessage());
        }
    }
}
