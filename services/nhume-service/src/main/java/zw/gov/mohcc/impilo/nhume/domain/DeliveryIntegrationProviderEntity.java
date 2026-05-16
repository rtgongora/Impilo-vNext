package zw.gov.mohcc.impilo.nhume.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "nhume_delivery_integration_providers")
public class DeliveryIntegrationProviderEntity {

    @Id
    @Column(name = "provider_code", nullable = false, length = 64)
    private String providerCode;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "provider_kind", nullable = false, length = 48)
    private String providerKind;

    @Column(name = "base_url", length = 512)
    private String baseUrl;

    @Column(name = "auth_kind", nullable = false, length = 32)
    private String authKind = "NONE";

    @Column(name = "auth_ref", length = 255)
    private String authRef;

    @Column(name = "webhook_secret", length = 255)
    private String webhookSecret;

    @Column(name = "config_json", nullable = false, columnDefinition = "TEXT")
    private String configJson = "{}";

    @Column(name = "simulation_mode", nullable = false)
    private boolean simulationMode = true;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "is_demo", nullable = false)
    private boolean demo;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public DeliveryIntegrationProviderEntity() {}

    public String getProviderCode() { return providerCode; }
    public void setProviderCode(String v) { this.providerCode = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String v) { this.displayName = v; }
    public String getProviderKind() { return providerKind; }
    public void setProviderKind(String v) { this.providerKind = v; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String v) { this.baseUrl = v; }
    public String getAuthKind() { return authKind; }
    public void setAuthKind(String v) { this.authKind = v; }
    public String getAuthRef() { return authRef; }
    public void setAuthRef(String v) { this.authRef = v; }
    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String v) { this.webhookSecret = v; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String v) { this.configJson = v; }
    public boolean isSimulationMode() { return simulationMode; }
    public void setSimulationMode(boolean v) { this.simulationMode = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
    public boolean isDemo() { return demo; }
    public void setDemo(boolean v) { this.demo = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
