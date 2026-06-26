package zw.gov.mohcc.impilo.learning.fundo.notify;

import org.springframework.stereotype.Component;

/**
 * Default notification provider: records acknowledgement of an intent without
 * contacting any channel. It NEVER reports delivery — the row terminates as
 * {@code RECORDED}, making the "configured but not delivering" state explicit and
 * auditable rather than silently faked.
 */
@Component
public class StubLearningNotificationProvider implements LearningNotificationProvider {

    public static final String KEY = "STUB";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public DeliveryOutcome deliver(Intent intent) {
        return DeliveryOutcome.recorded(
                "stub provider: intent recorded, not delivered (no comms channel configured)");
    }
}
