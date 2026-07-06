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

    /** Base URL of organization-registry-service (mirror receiver). */
    private String baseUrl = "http://localhost:8153";

    /** Path of the idempotent mirror receiver endpoint. */
    private String mirrorPath = "/v1/internal/org-registry/mirror/wgv";

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
