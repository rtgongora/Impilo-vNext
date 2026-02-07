package zw.gov.mohcc.impilo.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import zw.gov.mohcc.impilo.inventory.domain.CapabilityMode;

/**
 * Type-safe configuration properties for the Inventory service.
 *
 * <p>Bound from the {@code inventory.*} namespace in application.yml.
 * Controls outbox polling, default capability mode, FEFO strategy,
 * and integration URLs for downstream services.</p>
 */
@ConfigurationProperties(prefix = "inventory")
public class InventoryProperties {

    private Outbox outbox = new Outbox();
    private Capability capability = new Capability();
    private Fefo fefo = new Fefo();
    private Integration integration = new Integration();

    // ── Getters / setters ──────────────────────────────────────────────

    public Outbox getOutbox() { return outbox; }
    public void setOutbox(Outbox outbox) { this.outbox = outbox; }

    public Capability getCapability() { return capability; }
    public void setCapability(Capability capability) { this.capability = capability; }

    public Fefo getFefo() { return fefo; }
    public void setFefo(Fefo fefo) { this.fefo = fefo; }

    public Integration getIntegration() { return integration; }
    public void setIntegration(Integration integration) { this.integration = integration; }

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
     * Default capability mode for eLMIS integration.
     *
     * <p>{@code defaultMode}: the default integration mode when no
     * tenant/facility-specific capability is configured. Valid values:
     * INTERNAL, ADAPTER, HYBRID. Default: INTERNAL.</p>
     */
    public static class Capability {
        private CapabilityMode defaultMode = CapabilityMode.INTERNAL;

        public CapabilityMode getDefaultMode() { return defaultMode; }
        public void setDefaultMode(CapabilityMode defaultMode) { this.defaultMode = defaultMode; }
    }

    /**
     * FEFO (First-Expiry-First-Out) picking settings.
     *
     * <p>{@code enabled}: whether to enforce FEFO ordering when issuing
     * stock from inventory. Default: true.</p>
     */
    public static class Fefo {
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    /**
     * Integration URL settings for downstream services.
     *
     * <p>{@code pharmacyBaseUrl}: base URL for the pharmacy service. Default: http://localhost:8096.</p>
     * <p>{@code elmisAdapterBaseUrl}: base URL for the eLMIS adapter. Default: http://localhost:8099.</p>
     */
    public static class Integration {
        private String pharmacyBaseUrl = "http://localhost:8096";
        private String elmisAdapterBaseUrl = "http://localhost:8099";

        public String getPharmacyBaseUrl() { return pharmacyBaseUrl; }
        public void setPharmacyBaseUrl(String pharmacyBaseUrl) { this.pharmacyBaseUrl = pharmacyBaseUrl; }

        public String getElmisAdapterBaseUrl() { return elmisAdapterBaseUrl; }
        public void setElmisAdapterBaseUrl(String elmisAdapterBaseUrl) { this.elmisAdapterBaseUrl = elmisAdapterBaseUrl; }
    }
}
