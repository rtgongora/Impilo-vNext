package zw.gov.mohcc.impilo.msikaapps.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ma_installations")
public class InstallationEntity {
    @Id
    private String id = UUID.randomUUID().toString();
    @Column(name = "item_id", nullable = false)      private String itemId;
    @Column(name = "item_version", nullable = false) private String itemVersion;
    @Column(name = "tenant_id", nullable = false)    private String tenantId;
    @Column(name = "facility_ids",  columnDefinition = "TEXT") private String facilityIdsCsv;
    @Column(name = "workspace_ids", columnDefinition = "TEXT") private String workspaceIdsCsv;
    @Column(name = "programme_ids", columnDefinition = "TEXT") private String programmeIdsCsv;
    @Column(name = "roles_enabled", columnDefinition = "TEXT") private String rolesEnabledCsv;
    @Column(name = "configuration_json", columnDefinition = "TEXT") private String configurationJson;
    @Column(name = "integration_contract_id") private String integrationContractId;
    @Column(name = "external_app_id")         private String externalAppId;
    @Column(nullable = false)                 private String lifecycle = "INSTALLED";
    @Column(name = "installed_by", nullable = false) private String installedBy;
    @Column(name = "installed_at", nullable = false) private OffsetDateTime installedAt = OffsetDateTime.now();
    @Column(name = "configured_at") private OffsetDateTime configuredAt;
    @Column(name = "activated_at")  private OffsetDateTime activatedAt;
    @Column(name = "suspended_at")  private OffsetDateTime suspendedAt;
    @Column(name = "suspended_reason", columnDefinition = "TEXT") private String suspendedReason;
    @Column(name = "last_health_check_at") private OffsetDateTime lastHealthCheckAt;
    @Column(name = "health_status", nullable = false) private String healthStatus = "HEALTHY";
    @Column(name = "pod_id",     nullable = false) private String podId;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate void touch() { this.updatedAt = OffsetDateTime.now(); }

    public String getId() { return id; } public void setId(String s) { this.id = s; }
    public String getItemId() { return itemId; } public void setItemId(String s) { this.itemId = s; }
    public String getItemVersion() { return itemVersion; } public void setItemVersion(String s) { this.itemVersion = s; }
    public String getTenantId() { return tenantId; } public void setTenantId(String s) { this.tenantId = s; }
    public String getFacilityIdsCsv() { return facilityIdsCsv; } public void setFacilityIdsCsv(String s) { this.facilityIdsCsv = s; }
    public String getWorkspaceIdsCsv() { return workspaceIdsCsv; } public void setWorkspaceIdsCsv(String s) { this.workspaceIdsCsv = s; }
    public String getProgrammeIdsCsv() { return programmeIdsCsv; } public void setProgrammeIdsCsv(String s) { this.programmeIdsCsv = s; }
    public String getRolesEnabledCsv() { return rolesEnabledCsv; } public void setRolesEnabledCsv(String s) { this.rolesEnabledCsv = s; }
    public String getConfigurationJson() { return configurationJson; } public void setConfigurationJson(String s) { this.configurationJson = s; }
    public String getIntegrationContractId() { return integrationContractId; } public void setIntegrationContractId(String s) { this.integrationContractId = s; }
    public String getExternalAppId() { return externalAppId; } public void setExternalAppId(String s) { this.externalAppId = s; }
    public String getLifecycle() { return lifecycle; } public void setLifecycle(String s) { this.lifecycle = s; }
    public String getInstalledBy() { return installedBy; } public void setInstalledBy(String s) { this.installedBy = s; }
    public OffsetDateTime getInstalledAt() { return installedAt; } public void setInstalledAt(OffsetDateTime t) { this.installedAt = t; }
    public OffsetDateTime getConfiguredAt() { return configuredAt; } public void setConfiguredAt(OffsetDateTime t) { this.configuredAt = t; }
    public OffsetDateTime getActivatedAt() { return activatedAt; } public void setActivatedAt(OffsetDateTime t) { this.activatedAt = t; }
    public OffsetDateTime getSuspendedAt() { return suspendedAt; } public void setSuspendedAt(OffsetDateTime t) { this.suspendedAt = t; }
    public String getSuspendedReason() { return suspendedReason; } public void setSuspendedReason(String s) { this.suspendedReason = s; }
    public OffsetDateTime getLastHealthCheckAt() { return lastHealthCheckAt; } public void setLastHealthCheckAt(OffsetDateTime t) { this.lastHealthCheckAt = t; }
    public String getHealthStatus() { return healthStatus; } public void setHealthStatus(String s) { this.healthStatus = s; }
    public String getPodId() { return podId; } public void setPodId(String s) { this.podId = s; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime t) { this.createdAt = t; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(OffsetDateTime t) { this.updatedAt = t; }
}
