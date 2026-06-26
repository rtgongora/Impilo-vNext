package zw.gov.mohcc.impilo.learning.fundo.notify;

import java.util.Map;
import java.util.UUID;

/**
 * Provider-agnostic outbound notification adapter for Fundo learning intents.
 *
 * <p>The platform comms hub (Khuluma / notification-service) is owned elsewhere;
 * Fundo consumes it, never rebuilds it. This interface lets the learning-service
 * dispatch recorded intents through whichever provider is configured, defaulting
 * to a safe {@code STUB} that records acknowledgement <em>without faking delivery</em>.
 */
public interface LearningNotificationProvider {

    /** Configuration key that selects this provider ({@code learning.notifications.dispatch.provider}). */
    String key();

    /** Attempt to deliver one intent. Implementations must never claim delivery they did not perform. */
    DeliveryOutcome deliver(Intent intent);

    /** Minimal view of a {@code lrn_learning_notification} row to be delivered. */
    record Intent(
            UUID id,
            UUID tenantId,
            String subjectType,
            String subjectId,
            String eventCode,
            String title,
            String message,
            String channelPreference,
            Map<String, Object> metadata) {}

    /**
     * Result of a delivery attempt.
     *
     * @param status      terminal row status: {@code SENT} (real delivery), {@code RECORDED}
     *                    (acknowledged but NOT delivered — stub/not-configured), {@code FAILED}
     * @param delivered   true only when the message actually left the platform
     * @param detail      human-readable note stored in metadata for audit
     */
    record DeliveryOutcome(String status, boolean delivered, String detail) {
        public static DeliveryOutcome sent(String detail) {
            return new DeliveryOutcome("SENT", true, detail);
        }

        public static DeliveryOutcome recorded(String detail) {
            return new DeliveryOutcome("RECORDED", false, detail);
        }

        public static DeliveryOutcome failed(String detail) {
            return new DeliveryOutcome("FAILED", false, detail);
        }
    }
}
