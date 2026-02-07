package zw.gov.mohcc.impilo.oros.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe configuration properties for the OROS service.
 *
 * <p>Bound from the {@code oros.*} namespace in application.yml.
 * Controls outbox polling, SLA turnaround targets, acknowledgement
 * escalation timeouts, and default routing mode.</p>
 */
@ConfigurationProperties(prefix = "oros")
public class OrosProperties {

    private Outbox outbox = new Outbox();
    private Sla sla = new Sla();
    private Escalation escalation = new Escalation();
    private Routing routing = new Routing();

    // ── Getters / setters ──────────────────────────────────────────────

    public Outbox getOutbox() { return outbox; }
    public void setOutbox(Outbox outbox) { this.outbox = outbox; }

    public Sla getSla() { return sla; }
    public void setSla(Sla sla) { this.sla = sla; }

    public Escalation getEscalation() { return escalation; }
    public void setEscalation(Escalation escalation) { this.escalation = escalation; }

    public Routing getRouting() { return routing; }
    public void setRouting(Routing routing) { this.routing = routing; }

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
     * SLA (Service Level Agreement) settings.
     *
     * <p>{@code defaultTatMinutes}: the default turnaround-time target (in
     * minutes) for order completion from placement to result release.
     * Default: 120.</p>
     */
    public static class Sla {
        private int defaultTatMinutes = 120;

        public int getDefaultTatMinutes() { return defaultTatMinutes; }
        public void setDefaultTatMinutes(int defaultTatMinutes) { this.defaultTatMinutes = defaultTatMinutes; }
    }

    /**
     * Acknowledgement escalation settings.
     *
     * <p>{@code ackTimeoutMinutes}: number of minutes after which an
     * unacknowledged order triggers an escalation event. Default: 30.</p>
     */
    public static class Escalation {
        private int ackTimeoutMinutes = 30;

        public int getAckTimeoutMinutes() { return ackTimeoutMinutes; }
        public void setAckTimeoutMinutes(int ackTimeoutMinutes) { this.ackTimeoutMinutes = ackTimeoutMinutes; }
    }

    /**
     * Order routing settings.
     *
     * <p>{@code defaultMode}: the default routing mode when no
     * facility-level capability override is configured. Valid values:
     * INTERNAL, ADAPTER, HYBRID. Default: INTERNAL.</p>
     */
    public static class Routing {
        private String defaultMode = "INTERNAL";

        public String getDefaultMode() { return defaultMode; }
        public void setDefaultMode(String defaultMode) { this.defaultMode = defaultMode; }
    }
}
