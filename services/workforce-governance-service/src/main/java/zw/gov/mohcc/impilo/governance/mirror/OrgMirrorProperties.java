package zw.gov.mohcc.impilo.governance.mirror;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the one-way organisation mirror producer
 * (workforce-governance {@code wgv_organisation} → organization-registry).
 *
 * <p>The whole producer is gated behind {@link #enabled} which defaults to
 * {@code false}: when off there is zero mirror traffic and no behavioural
 * change to existing governance flows. Enabling it is an explicit ops decision.
 */
@ConfigurationProperties(prefix = "impilo.org-mirror")
public class OrgMirrorProperties {

    /** Master switch for the mirror producer. Default OFF. */
    private boolean enabled = false;

    /**
     * Phase-2a dormant read-preference. {@code WGV} (default) keeps every read on
     * the legacy {@code wgv_organisation} path — a strict no-op vs today.
     * {@code ORG_REGISTRY} opts a resolved read into the org-registry id when a
     * dual-key mapping exists, else falls back to WGV. Flipping this to
     * ORG_REGISTRY is a phase-2c step and must not be done until the mirror is
     * soak-verified complete and the dual-key columns are backfilled.
     */
    private ReadPreference readPreference = ReadPreference.WGV;

    /** Read source preference for organisation resolution. */
    public enum ReadPreference {
        WGV,
        ORG_REGISTRY
    }

    /** Base URL of organization-registry-service (mirror receiver + inventory reader). */
    private String baseUrl = "http://localhost:8153";

    /** Path of the idempotent mirror receiver endpoint. */
    private String mirrorPath = "/v1/internal/org-registry/mirror/wgv";

    /** Path of the read-only mirror inventory endpoint (reconciliation source). */
    private String inventoryPath = "/internal/v1/org-registry/mirror/wgv/inventory";

    /** Connect timeout for the mirror HTTP call, in milliseconds. */
    private int connectTimeoutMs = 2000;

    /** Read timeout for the mirror HTTP call, in milliseconds. */
    private int readTimeoutMs = 3000;

    /** Page size for the backfill sweep over active organisations. */
    private int backfillPageSize = 200;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ReadPreference getReadPreference() {
        return readPreference;
    }

    public void setReadPreference(ReadPreference readPreference) {
        this.readPreference = readPreference;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getMirrorPath() {
        return mirrorPath;
    }

    public void setMirrorPath(String mirrorPath) {
        this.mirrorPath = mirrorPath;
    }

    public String getInventoryPath() {
        return inventoryPath;
    }

    public void setInventoryPath(String inventoryPath) {
        this.inventoryPath = inventoryPath;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getBackfillPageSize() {
        return backfillPageSize;
    }

    public void setBackfillPageSize(int backfillPageSize) {
        this.backfillPageSize = backfillPageSize;
    }
}
