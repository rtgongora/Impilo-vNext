package zw.gov.mohcc.impilo.pharmacy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import zw.gov.mohcc.impilo.pharmacy.domain.ElmisMode;

/**
 * Type-safe configuration properties for the Pharmacy service.
 *
 * <p>Bound from the {@code pharmacy.*} namespace in application.yml.
 * Controls outbox polling, pickup proof settings, substitution policy,
 * stock management strategy, and eLMIS integration mode.</p>
 */
@ConfigurationProperties(prefix = "pharmacy")
public class PharmacyProperties {

    private Outbox outbox = new Outbox();
    private Pickup pickup = new Pickup();
    private Substitution substitution = new Substitution();
    private Stock stock = new Stock();
    private Elmis elmis = new Elmis();
    private Hmac hmac = new Hmac();

    // ── Getters / setters ──────────────────────────────────────────────

    public Outbox getOutbox() { return outbox; }
    public void setOutbox(Outbox outbox) { this.outbox = outbox; }

    public Pickup getPickup() { return pickup; }
    public void setPickup(Pickup pickup) { this.pickup = pickup; }

    public Substitution getSubstitution() { return substitution; }
    public void setSubstitution(Substitution substitution) { this.substitution = substitution; }

    public Stock getStock() { return stock; }
    public void setStock(Stock stock) { this.stock = stock; }

    public Elmis getElmis() { return elmis; }
    public void setElmis(Elmis elmis) { this.elmis = elmis; }

    public Hmac getHmac() { return hmac; }
    public void setHmac(Hmac hmac) { this.hmac = hmac; }

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
     * Pickup proof settings.
     *
     * <p>{@code otpLength}: number of digits in the OTP code. Default: 6.</p>
     * <p>{@code expiryMinutes}: how long the pickup proof remains valid
     * (in minutes). Default: 1440 (24 hours).</p>
     */
    public static class Pickup {
        private int otpLength = 6;
        private int expiryMinutes = 1440;

        public int getOtpLength() { return otpLength; }
        public void setOtpLength(int otpLength) { this.otpLength = otpLength; }

        public int getExpiryMinutes() { return expiryMinutes; }
        public void setExpiryMinutes(int expiryMinutes) { this.expiryMinutes = expiryMinutes; }
    }

    /**
     * Substitution policy settings.
     *
     * <p>{@code requireStepUp}: whether substitutions require step-up
     * authorization from a senior pharmacist. Default: false.</p>
     */
    public static class Substitution {
        private boolean requireStepUp = false;

        public boolean isRequireStepUp() { return requireStepUp; }
        public void setRequireStepUp(boolean requireStepUp) { this.requireStepUp = requireStepUp; }
    }

    /**
     * Stock management settings.
     *
     * <p>{@code fefoEnabled}: whether to enforce First-Expiry-First-Out
     * picking during dispensing. Default: true.</p>
     */
    public static class Stock {
        private boolean fefoEnabled = true;

        public boolean isFefoEnabled() { return fefoEnabled; }
        public void setFefoEnabled(boolean fefoEnabled) { this.fefoEnabled = fefoEnabled; }
    }

    /**
     * eLMIS integration settings.
     *
     * <p>{@code mode}: the integration mode with the external eLMIS system.
     * Valid values: INTERNAL (no external system), ADAPTER (route through
     * pharmacy-elmis-adapter), HYBRID (both internal + adapter with
     * reconciliation). Default: INTERNAL.</p>
     */
    public static class Elmis {
        private ElmisMode mode = ElmisMode.INTERNAL;

        public ElmisMode getMode() { return mode; }
        public void setMode(ElmisMode mode) { this.mode = mode; }
    }

    /**
     * HMAC pepper for pickup proof token hashing (server-side secret).
     */
    public static class Hmac {
        private String pepper = "changeme-pharmacy-hmac-not-for-production";

        public String getPepper() { return pepper; }
        public void setPepper(String pepper) { this.pepper = pepper; }
    }
}
