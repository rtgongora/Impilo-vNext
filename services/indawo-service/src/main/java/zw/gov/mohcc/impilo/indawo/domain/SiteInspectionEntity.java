package zw.gov.mohcc.impilo.indawo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "ind_site_inspections")
public class SiteInspectionEntity {

    @Id
    @Column(name = "inspection_id", nullable = false)
    private UUID inspectionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "inspection_type", nullable = false, length = 32)
    private String inspectionType;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "actual_date")
    private LocalDate actualDate;

    @Column(name = "inspector_assignments", columnDefinition = "JSONB")
    private String inspectorAssignmentsJson;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "SCHEDULED";

    @Column(name = "report_status", nullable = false, length = 32)
    private String reportStatus = "DRAFT";

    @Column(name = "outcome", length = 64)
    private String outcome;

    @Column(name = "recommendation", length = 64)
    private String recommendation;

    @Column(name = "severity_summary", length = 32)
    private String severitySummary;

    @Column(name = "next_action", length = 64)
    private String nextAction;

    @Column(name = "next_inspection_due")
    private LocalDate nextInspectionDue;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "metadata", columnDefinition = "JSONB")
    private String metadataJson;

    @Column(name = "score_total")
    private BigDecimal scoreTotal;

    @Column(name = "score_max")
    private BigDecimal scoreMax;

    @Column(name = "score_percent")
    private BigDecimal scorePercent;

    @Column(name = "critical_fail_count")
    private Integer criticalFailCount;

    @Column(name = "major_fail_count")
    private Integer majorFailCount;

    @Column(name = "minor_fail_count")
    private Integer minorFailCount;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected SiteInspectionEntity() {}

    public SiteInspectionEntity(UUID inspectionId, UUID tenantId, UUID siteId, String inspectionType) {
        this.inspectionId = inspectionId;
        this.tenantId = tenantId;
        this.siteId = siteId;
        this.inspectionType = inspectionType;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getInspectionId() { return inspectionId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getSiteId() { return siteId; }
    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; touch(null); }
    public String getInspectionType() { return inspectionType; }
    public UUID getTemplateId() { return templateId; }
    public void setTemplateId(UUID templateId) { this.templateId = templateId; touch(null); }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; touch(null); }
    public LocalDate getActualDate() { return actualDate; }
    public void setActualDate(LocalDate actualDate) { this.actualDate = actualDate; touch(null); }
    public String getInspectorAssignmentsJson() { return inspectorAssignmentsJson; }
    public void setInspectorAssignmentsJson(String inspectorAssignmentsJson) { this.inspectorAssignmentsJson = inspectorAssignmentsJson; touch(null); }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; touch(null); }
    public String getReportStatus() { return reportStatus; }
    public void setReportStatus(String reportStatus) { this.reportStatus = reportStatus; touch(null); }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; touch(null); }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; touch(null); }
    public String getSeveritySummary() { return severitySummary; }
    public void setSeveritySummary(String severitySummary) { this.severitySummary = severitySummary; touch(null); }
    public String getNextAction() { return nextAction; }
    public void setNextAction(String nextAction) { this.nextAction = nextAction; touch(null); }
    public LocalDate getNextInspectionDue() { return nextInspectionDue; }
    public void setNextInspectionDue(LocalDate nextInspectionDue) { this.nextInspectionDue = nextInspectionDue; touch(null); }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; touch(null); }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; touch(null); }
    public BigDecimal getScoreTotal() { return scoreTotal; }
    public void setScoreTotal(BigDecimal scoreTotal) { this.scoreTotal = scoreTotal; touch(null); }
    public BigDecimal getScoreMax() { return scoreMax; }
    public void setScoreMax(BigDecimal scoreMax) { this.scoreMax = scoreMax; touch(null); }
    public BigDecimal getScorePercent() { return scorePercent; }
    public void setScorePercent(BigDecimal scorePercent) { this.scorePercent = scorePercent; touch(null); }
    public Integer getCriticalFailCount() { return criticalFailCount; }
    public void setCriticalFailCount(Integer criticalFailCount) { this.criticalFailCount = criticalFailCount; touch(null); }
    public Integer getMajorFailCount() { return majorFailCount; }
    public void setMajorFailCount(Integer majorFailCount) { this.majorFailCount = majorFailCount; touch(null); }
    public Integer getMinorFailCount() { return minorFailCount; }
    public void setMinorFailCount(Integer minorFailCount) { this.minorFailCount = minorFailCount; touch(null); }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void touch(String actorRef) {
        if (actorRef != null) this.updatedBy = actorRef;
        this.updatedAt = OffsetDateTime.now();
    }
}

