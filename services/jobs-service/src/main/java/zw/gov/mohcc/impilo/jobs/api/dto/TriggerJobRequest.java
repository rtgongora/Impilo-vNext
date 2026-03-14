package zw.gov.mohcc.impilo.jobs.api.dto;

import java.util.UUID;

/**
 * Request body for triggering a job execution.
 */
public class TriggerJobRequest {

    private UUID tenantId;

    private String idempotencyKey;

    private String config;

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }
}
