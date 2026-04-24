package zw.gov.mohcc.impilo.msikaflow.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.msikaflow.domain.LogisticsProviderType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mf_logistics_providers")
public class LogisticsProviderEntity {

    @Id
    @Column(name = "provider_id", nullable = false, length = 26)
    private String providerId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 30)
    private LogisticsProviderType providerType;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "supported_modes", nullable = false, columnDefinition = "jsonb")
    private String supportedModesJson = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "service_areas", nullable = false, columnDefinition = "jsonb")
    private String serviceAreasJson = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "capabilities", nullable = false, columnDefinition = "jsonb")
    private String capabilitiesJson = "{}";

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (supportedModesJson == null) supportedModesJson = "[]";
        if (serviceAreasJson == null) serviceAreasJson = "[]";
        if (capabilitiesJson == null) capabilitiesJson = "{}";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public LogisticsProviderType getProviderType() { return providerType; }
    public void setProviderType(LogisticsProviderType providerType) { this.providerType = providerType; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSupportedModesJson() { return supportedModesJson; }
    public void setSupportedModesJson(String supportedModesJson) { this.supportedModesJson = supportedModesJson; }
    public String getServiceAreasJson() { return serviceAreasJson; }
    public void setServiceAreasJson(String serviceAreasJson) { this.serviceAreasJson = serviceAreasJson; }
    public String getCapabilitiesJson() { return capabilitiesJson; }
    public void setCapabilitiesJson(String capabilitiesJson) { this.capabilitiesJson = capabilitiesJson; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}

