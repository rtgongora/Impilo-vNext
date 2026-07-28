package zw.gov.mohcc.impilo.support.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A scoped technical/operational support team (support-service SoR). */
@Entity
@Table(name = "sup_support_team")
public class SupportTeamEntity {
    @Id @Column(name = "team_id") private UUID teamId;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "team_code", nullable = false, length = 64) private String teamCode;
    @Column(nullable = false, length = 255) private String name;
    @Column(name = "support_tier", nullable = false, length = 32) private String supportTier = "TIER_1";
    @Column(name = "supported_service") private String supportedService;
    @Column(name = "geographic_scope") private String geographicScope;
    @Column(name = "facility_scope_ref") private String facilityScopeRef;
    @Column(name = "organisation_scope") private String organisationScope;
    @Column(name = "environment_scope") private String environmentScope;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "metadata_json", columnDefinition = "TEXT") private String metadataJson;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    protected SupportTeamEntity() {}

    public SupportTeamEntity(UUID teamId, UUID tenantId, String teamCode, String name) {
        this.teamId = teamId;
        this.tenantId = tenantId;
        this.teamCode = teamCode;
        this.name = name;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getTeamId() { return teamId; }
    public UUID getTenantId() { return tenantId; }
    public String getTeamCode() { return teamCode; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getSupportTier() { return supportTier; }
    public void setSupportTier(String v) { this.supportTier = v; }
    public String getSupportedService() { return supportedService; }
    public void setSupportedService(String v) { this.supportedService = v; }
    public String getGeographicScope() { return geographicScope; }
    public void setGeographicScope(String v) { this.geographicScope = v; }
    public String getFacilityScopeRef() { return facilityScopeRef; }
    public void setFacilityScopeRef(String v) { this.facilityScopeRef = v; }
    public String getOrganisationScope() { return organisationScope; }
    public void setOrganisationScope(String v) { this.organisationScope = v; }
    public String getEnvironmentScope() { return environmentScope; }
    public void setEnvironmentScope(String v) { this.environmentScope = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String v) { this.metadataJson = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
