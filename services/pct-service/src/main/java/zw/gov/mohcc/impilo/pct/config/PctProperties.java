package zw.gov.mohcc.impilo.pct.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe configuration properties for the PCT service.
 *
 * <p>Bound from the {@code pct.*} namespace in application.yml.
 * Controls outbox polling, queue SLA defaults, and discharge workflow rules.</p>
 */
@ConfigurationProperties(prefix = "pct")
public class PctProperties {

    private Outbox outbox = new Outbox();
    private Queue queue = new Queue();
    private Discharge discharge = new Discharge();

    // ── Getters / setters ──────────────────────────────────────────────

    public Outbox getOutbox() { return outbox; }
    public void setOutbox(Outbox outbox) { this.outbox = outbox; }

    public Queue getQueue() { return queue; }
    public void setQueue(Queue queue) { this.queue = queue; }

    public Discharge getDischarge() { return discharge; }
    public void setDischarge(Discharge discharge) { this.discharge = discharge; }

    /** Convenience accessor used by ControlTowerService. */
    public int getSlaWaitMinutes() { return queue.getDefaultSlaMinutes(); }

    // ── Nested configuration classes ───────────────────────────────────

    /**
     * Event outbox polling settings.
     *
     * <p>{@code pollIntervalMs}: how often the outbox publisher polls for
     * unpublished events (in milliseconds). Default: 2000.</p>
     */
    public static class Outbox {
        private long pollIntervalMs = 2000;

        public long getPollIntervalMs() { return pollIntervalMs; }
        public void setPollIntervalMs(long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }
    }

    /**
     * Queue management settings.
     *
     * <p>{@code defaultSlaMinutes}: the default SLA target (in minutes) for
     * patients waiting in a queue before being seen. Default: 30.</p>
     */
    public static class Queue {
        private int defaultSlaMinutes = 30;

        public int getDefaultSlaMinutes() { return defaultSlaMinutes; }
        public void setDefaultSlaMinutes(int defaultSlaMinutes) { this.defaultSlaMinutes = defaultSlaMinutes; }
    }

    /**
     * Discharge workflow settings.
     *
     * <p>{@code requirePaymentClearance}: when true, the discharge workflow
     * will block until billing/payment clearance is confirmed. When false,
     * payment clearance is skipped (e.g. for public-sector facilities with
     * no patient billing). Default: true.</p>
     */
    public static class Discharge {
        private boolean requirePaymentClearance = true;

        public boolean isRequirePaymentClearance() { return requirePaymentClearance; }
        public void setRequirePaymentClearance(boolean requirePaymentClearance) { this.requirePaymentClearance = requirePaymentClearance; }
    }
}
