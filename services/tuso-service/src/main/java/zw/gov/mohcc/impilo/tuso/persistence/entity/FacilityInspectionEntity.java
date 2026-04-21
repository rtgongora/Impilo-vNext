package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "facility_inspection", schema = "tuso")
public class FacilityInspectionEntity {

    @Id
    @Column(name = "inspection_id", nullable = false, updatable = false)
    private UUID inspectionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private FacilityEntity facility;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private FacilityApplicationEntity application;

    @Enumerated(EnumType.STRING)
    @Column(name = "inspection_type", nullable = false, length = 64)
    private FacilityInspectionType inspectionType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private InspectionChecklistTemplateEntity template;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "actual_date")
    private LocalDate actualDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "inspector_assignments", columnDefinition = "jsonb")
    private List<Map<String, Object>> inspectorAssignments;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 64)
    private FacilityInspectionStatus status = FacilityInspectionStatus.SCHEDULED;

    @Column(name = "report_status", nullable = false, length = 64)
    private String reportStatus = "DRAFT";

    @Column(name = "outcome", length = 64)
    private String outcome;

    @Column(name = "recommendation", length = 128)
    private String recommendation;

    @Column(name = "severity_summary", length = 64)
    private String severitySummary;

    @Column(name = "next_action", length = 128)
    private String nextAction;

    @Column(name = "next_inspection_due_date")
    private LocalDate nextInspectionDueDate;

    @Column(name = "capture_mode", nullable = false, length = 32)
    private String captureMode = "ONLINE";

    @Column(name = "offline_capture_reference", length = 128)
    private String offlineCaptureReference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_summary", columnDefinition = "jsonb")
    private Map<String, Object> evidenceSummary;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (inspectionId == null) {
            inspectionId = UUID.randomUUID();
        }
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getInspectionId() { return inspectionId; }
    public void setInspectionId(UUID inspectionId) { this.inspectionId = inspectionId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public FacilityEntity getFacility() { return facility; }
    public void setFacility(FacilityEntity facility) { this.facility = facility; }
    public FacilityApplicationEntity getApplication() { return application; }
    public void setApplication(FacilityApplicationEntity application) { this.application = application; }
    public FacilityInspectionType getInspectionType() { return inspectionType; }
    public void setInspectionType(FacilityInspectionType inspectionType) { this.inspectionType = inspectionType; }
    public InspectionChecklistTemplateEntity getTemplate() { return template; }
    public void setTemplate(InspectionChecklistTemplateEntity template) { this.template = template; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }
    public LocalDate getActualDate() { return actualDate; }
    public void setActualDate(LocalDate actualDate) { this.actualDate = actualDate; }
    public List<Map<String, Object>> getInspectorAssignments() { return inspectorAssignments; }
    public void setInspectorAssignments(List<Map<String, Object>> inspectorAssignments) { this.inspectorAssignments = inspectorAssignments; }
    public FacilityInspectionStatus getStatus() { return status; }
    public void setStatus(FacilityInspectionStatus status) { this.status = status; }
    public String getReportStatus() { return reportStatus; }
    public void setReportStatus(String reportStatus) { this.reportStatus = reportStatus; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    public String getSeveritySummary() { return severitySummary; }
    public void setSeveritySummary(String severitySummary) { this.severitySummary = severitySummary; }
    public String getNextAction() { return nextAction; }
    public void setNextAction(String nextAction) { this.nextAction = nextAction; }
    public LocalDate getNextInspectionDueDate() { return nextInspectionDueDate; }
    public void setNextInspectionDueDate(LocalDate nextInspectionDueDate) { this.nextInspectionDueDate = nextInspectionDueDate; }
    public String getCaptureMode() { return captureMode; }
    public void setCaptureMode(String captureMode) { this.captureMode = captureMode; }
    public String getOfflineCaptureReference() { return offlineCaptureReference; }
    public void setOfflineCaptureReference(String offlineCaptureReference) { this.offlineCaptureReference = offlineCaptureReference; }
    public Map<String, Object> getEvidenceSummary() { return evidenceSummary; }
    public void setEvidenceSummary(Map<String, Object> evidenceSummary) { this.evidenceSummary = evidenceSummary; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
