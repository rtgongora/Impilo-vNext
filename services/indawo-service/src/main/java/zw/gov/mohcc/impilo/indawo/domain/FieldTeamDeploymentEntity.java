package zw.gov.mohcc.impilo.indawo.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** A field-team deployment to an outbreak / site (Indawo SoR). */
@Entity
@Table(name = "ind_field_team_deployments")
public class FieldTeamDeploymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deployment_id", nullable = false, updatable = false)
    private UUID deploymentId = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @Column(name = "outbreak_id")
    private UUID outbreakId;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "deployed_at", nullable = false)
    private OffsetDateTime deployedAt = OffsetDateTime.now();

    @Column(name = "recalled_at")
    private OffsetDateTime recalledAt;

    @Column(name = "objective", length = 512)
    private String objective;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @PrePersist
    void onCreate() { createdAt = OffsetDateTime.now(); updatedAt = createdAt; }
    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public UUID getDeploymentId() { return deploymentId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getTeamId() { return teamId; }
    public void setTeamId(UUID teamId) { this.teamId = teamId; }
    public UUID getOutbreakId() { return outbreakId; }
    public void setOutbreakId(UUID outbreakId) { this.outbreakId = outbreakId; }
    public UUID getSiteId() { return siteId; }
    public void setSiteId(UUID siteId) { this.siteId = siteId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getDeployedAt() { return deployedAt; }
    public void setDeployedAt(OffsetDateTime deployedAt) { this.deployedAt = deployedAt; }
    public OffsetDateTime getRecalledAt() { return recalledAt; }
    public void setRecalledAt(OffsetDateTime recalledAt) { this.recalledAt = recalledAt; }
    public String getObjective() { return objective; }
    public void setObjective(String objective) { this.objective = objective; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
