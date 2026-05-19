package zw.gov.mohcc.impilo.dispatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dsp_delivery_integrations")
public class DeliveryIntegrationProviderEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "provider_code", nullable = false)
    private String providerCode;

    @Column(name = "provider_name", nullable = false)
    private String providerName;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;

    @Column(name = "last_webhook_at")
    private OffsetDateTime lastWebhookAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected DeliveryIntegrationProviderEntity() {}

    public DeliveryIntegrationProviderEntity(UUID id, UUID tenantId, String providerCode, String providerName) {
        this.id = id;
        this.tenantId = tenantId;
        this.providerCode = providerCode;
        this.providerName = providerName;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getProviderCode() { return providerCode; }
    public String getProviderName() { return providerName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public OffsetDateTime getLastWebhookAt() { return lastWebhookAt; }
    public void setLastWebhookAt(OffsetDateTime lastWebhookAt) { this.lastWebhookAt = lastWebhookAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
