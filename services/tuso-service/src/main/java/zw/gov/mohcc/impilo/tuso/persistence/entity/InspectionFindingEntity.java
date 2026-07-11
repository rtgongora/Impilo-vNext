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
@Table(name = "inspection_finding", schema = "tuso")
public class InspectionFindingEntity {

    @Id
    @Column(name = "finding_id", nullable = false, updatable = false)
    private UUID findingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_id", nullable = false)
    private FacilityInspectionEntity inspection;

    @Column(name = "checklist_item_code", nullable = false, length = 64)
    private String checklistItemCode;

    @Column(name = "checklist_section", length = 128)
    private String checklistSection;

    @Column(name = "requirement_text", nullable = false, columnDefinition = "text")
    private String requirementText;

    @Column(name = "critical_flag", nullable = false)
    private boolean criticalFlag;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private InspectionFindingStatus status;

    @Column(name = "severity", nullable = false, length = 32)
    private String severity;

    @Column(name = "comments", columnDefinition = "text")
    private String comments;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_refs", columnDefinition = "jsonb")
    private List<Map<String, Object>> evidenceRefs;

    @Column(name = "rectification_deadline")
    private LocalDate rectificationDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "rectification_status", nullable = false, length = 64)
    private RectificationStatus rectificationStatus = RectificationStatus.NOT_REQUIRED;

    @Column(name = "response_id")
    private UUID responseId;

    @Column(name = "extension_due_date")
    private LocalDate extensionDueDate;

    @Column(name = "extension_reason", columnDefinition = "text")
    private String extensionReason;

    @Column(name = "recurrence_of_finding_id")
    private UUID recurrenceOfFindingId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (findingId == null) {
            findingId = UUID.randomUUID();
        }
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getFindingId() { return findingId; }
    public void setFindingId(UUID findingId) { this.findingId = findingId; }
    public FacilityInspectionEntity getInspection() { return inspection; }
    public void setInspection(FacilityInspectionEntity inspection) { this.inspection = inspection; }
    public String getChecklistItemCode() { return checklistItemCode; }
    public void setChecklistItemCode(String checklistItemCode) { this.checklistItemCode = checklistItemCode; }
    public String getChecklistSection() { return checklistSection; }
    public void setChecklistSection(String checklistSection) { this.checklistSection = checklistSection; }
    public String getRequirementText() { return requirementText; }
    public void setRequirementText(String requirementText) { this.requirementText = requirementText; }
    public boolean isCriticalFlag() { return criticalFlag; }
    public void setCriticalFlag(boolean criticalFlag) { this.criticalFlag = criticalFlag; }
    public InspectionFindingStatus getStatus() { return status; }
    public void setStatus(InspectionFindingStatus status) { this.status = status; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
    public List<Map<String, Object>> getEvidenceRefs() { return evidenceRefs; }
    public void setEvidenceRefs(List<Map<String, Object>> evidenceRefs) { this.evidenceRefs = evidenceRefs; }
    public LocalDate getRectificationDeadline() { return rectificationDeadline; }
    public void setRectificationDeadline(LocalDate rectificationDeadline) { this.rectificationDeadline = rectificationDeadline; }
    public RectificationStatus getRectificationStatus() { return rectificationStatus; }
    public void setRectificationStatus(RectificationStatus rectificationStatus) { this.rectificationStatus = rectificationStatus; }
    public UUID getResponseId() { return responseId; }
    public void setResponseId(UUID responseId) { this.responseId = responseId; }
    public LocalDate getExtensionDueDate() { return extensionDueDate; }
    public void setExtensionDueDate(LocalDate extensionDueDate) { this.extensionDueDate = extensionDueDate; }
    public String getExtensionReason() { return extensionReason; }
    public void setExtensionReason(String extensionReason) { this.extensionReason = extensionReason; }
    public UUID getRecurrenceOfFindingId() { return recurrenceOfFindingId; }
    public void setRecurrenceOfFindingId(UUID recurrenceOfFindingId) { this.recurrenceOfFindingId = recurrenceOfFindingId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
