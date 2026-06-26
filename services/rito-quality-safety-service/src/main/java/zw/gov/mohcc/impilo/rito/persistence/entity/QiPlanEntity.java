package zw.gov.mohcc.impilo.rito.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rit_qi_plan", schema = "rito")
public class QiPlanEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "qi_reference", nullable = false, length = 48)
    private String qiReference;

    @Column(name = "title", nullable = false, length = 512)
    private String title;

    @Column(name = "aim_statement", columnDefinition = "TEXT")
    private String aimStatement;

    @Column(name = "methodology", nullable = false, length = 48)
    private String methodology;

    @Column(name = "problem_statement", columnDefinition = "TEXT")
    private String problemStatement;

    @Column(name = "baseline_measure")
    private BigDecimal baselineMeasure;

    @Column(name = "target_measure")
    private BigDecimal targetMeasure;

    @Column(name = "measure_unit", length = 64)
    private String measureUnit;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "district_id")
    private UUID districtId;

    @Column(name = "sponsor_actor_id", length = 128)
    private String sponsorActorId;

    @Column(name = "lead_actor_id", length = 128)
    private String leadActorId;

    @Column(name = "source_case_id")
    private UUID sourceCaseId;

    @Column(name = "source_finding_id")
    private UUID sourceFindingId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "outcome_summary", columnDefinition = "TEXT")
    private String outcomeSummary;

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
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getQiReference() { return qiReference; }
    public void setQiReference(String qiReference) { this.qiReference = qiReference; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAimStatement() { return aimStatement; }
    public void setAimStatement(String aimStatement) { this.aimStatement = aimStatement; }
    public String getMethodology() { return methodology; }
    public void setMethodology(String methodology) { this.methodology = methodology; }
    public String getProblemStatement() { return problemStatement; }
    public void setProblemStatement(String problemStatement) { this.problemStatement = problemStatement; }
    public BigDecimal getBaselineMeasure() { return baselineMeasure; }
    public void setBaselineMeasure(BigDecimal baselineMeasure) { this.baselineMeasure = baselineMeasure; }
    public BigDecimal getTargetMeasure() { return targetMeasure; }
    public void setTargetMeasure(BigDecimal targetMeasure) { this.targetMeasure = targetMeasure; }
    public String getMeasureUnit() { return measureUnit; }
    public void setMeasureUnit(String measureUnit) { this.measureUnit = measureUnit; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public UUID getDistrictId() { return districtId; }
    public void setDistrictId(UUID districtId) { this.districtId = districtId; }
    public String getSponsorActorId() { return sponsorActorId; }
    public void setSponsorActorId(String sponsorActorId) { this.sponsorActorId = sponsorActorId; }
    public String getLeadActorId() { return leadActorId; }
    public void setLeadActorId(String leadActorId) { this.leadActorId = leadActorId; }
    public UUID getSourceCaseId() { return sourceCaseId; }
    public void setSourceCaseId(UUID sourceCaseId) { this.sourceCaseId = sourceCaseId; }
    public UUID getSourceFindingId() { return sourceFindingId; }
    public void setSourceFindingId(UUID sourceFindingId) { this.sourceFindingId = sourceFindingId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public String getOutcomeSummary() { return outcomeSummary; }
    public void setOutcomeSummary(String outcomeSummary) { this.outcomeSummary = outcomeSummary; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
