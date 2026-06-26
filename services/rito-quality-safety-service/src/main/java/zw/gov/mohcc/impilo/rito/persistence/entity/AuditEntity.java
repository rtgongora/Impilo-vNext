package zw.gov.mohcc.impilo.rito.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rit_audit", schema = "rito")
public class AuditEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "audit_reference", nullable = false, length = 48)
    private String auditReference;

    @Column(name = "audit_tool_id")
    private UUID auditToolId;

    @Column(name = "audit_type", nullable = false, length = 48)
    private String auditType;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "district_id")
    private UUID districtId;

    @Column(name = "province_id")
    private UUID provinceId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "lead_auditor_actor_id", length = 128)
    private String leadAuditorActorId;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "score_numerator")
    private BigDecimal scoreNumerator;

    @Column(name = "score_denominator")
    private BigDecimal scoreDenominator;

    @Column(name = "score_percent")
    private BigDecimal scorePercent;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private String metadataJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getAuditReference() { return auditReference; }
    public void setAuditReference(String auditReference) { this.auditReference = auditReference; }
    public UUID getAuditToolId() { return auditToolId; }
    public void setAuditToolId(UUID auditToolId) { this.auditToolId = auditToolId; }
    public String getAuditType() { return auditType; }
    public void setAuditType(String auditType) { this.auditType = auditType; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public UUID getDistrictId() { return districtId; }
    public void setDistrictId(UUID districtId) { this.districtId = districtId; }
    public UUID getProvinceId() { return provinceId; }
    public void setProvinceId(UUID provinceId) { this.provinceId = provinceId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getLeadAuditorActorId() { return leadAuditorActorId; }
    public void setLeadAuditorActorId(String leadAuditorActorId) { this.leadAuditorActorId = leadAuditorActorId; }
    public OffsetDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(OffsetDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(OffsetDateTime closedAt) { this.closedAt = closedAt; }
    public BigDecimal getScoreNumerator() { return scoreNumerator; }
    public void setScoreNumerator(BigDecimal scoreNumerator) { this.scoreNumerator = scoreNumerator; }
    public BigDecimal getScoreDenominator() { return scoreDenominator; }
    public void setScoreDenominator(BigDecimal scoreDenominator) { this.scoreDenominator = scoreDenominator; }
    public BigDecimal getScorePercent() { return scorePercent; }
    public void setScorePercent(BigDecimal scorePercent) { this.scorePercent = scorePercent; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
