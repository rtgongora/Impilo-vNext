package zw.gov.mohcc.impilo.indawo.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** A declared public-health outbreak spanning places / areas (Indawo SoR). */
@Entity
@Table(name = "ind_outbreaks")
public class OutbreakEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "outbreak_id", nullable = false, updatable = false)
    private UUID outbreakId = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "condition_code", nullable = false, length = 64)
    private String conditionCode;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "severity", nullable = false, length = 16)
    private String severity = "MEDIUM";

    @Column(name = "status", nullable = false, length = 32)
    private String status = "SUSPECTED";

    @Column(name = "area_description", length = 512)
    private String areaDescription;

    @Column(name = "primary_site_id")
    private UUID primarySiteId;

    @Column(name = "declared_at")
    private OffsetDateTime declaredAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "case_count", nullable = false)
    private int caseCount;

    @Column(name = "death_count", nullable = false)
    private int deathCount;

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
    public UUID getOutbreakId() { return outbreakId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getConditionCode() { return conditionCode; }
    public void setConditionCode(String conditionCode) { this.conditionCode = conditionCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAreaDescription() { return areaDescription; }
    public void setAreaDescription(String areaDescription) { this.areaDescription = areaDescription; }
    public UUID getPrimarySiteId() { return primarySiteId; }
    public void setPrimarySiteId(UUID primarySiteId) { this.primarySiteId = primarySiteId; }
    public OffsetDateTime getDeclaredAt() { return declaredAt; }
    public void setDeclaredAt(OffsetDateTime declaredAt) { this.declaredAt = declaredAt; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(OffsetDateTime closedAt) { this.closedAt = closedAt; }
    public int getCaseCount() { return caseCount; }
    public void setCaseCount(int caseCount) { this.caseCount = caseCount; }
    public int getDeathCount() { return deathCount; }
    public void setDeathCount(int deathCount) { this.deathCount = deathCount; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
