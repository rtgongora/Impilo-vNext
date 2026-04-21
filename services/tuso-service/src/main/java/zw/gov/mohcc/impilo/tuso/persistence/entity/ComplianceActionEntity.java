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
@Table(name = "compliance_action", schema = "tuso")
public class ComplianceActionEntity {

    @Id
    @Column(name = "action_id", nullable = false, updatable = false)
    private UUID actionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private FacilityEntity facility;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_finding_id", nullable = false)
    private InspectionFindingEntity inspectionFinding;

    @Column(name = "action_type", nullable = false, length = 64)
    private String actionType;

    @Column(name = "owner_name", length = 255)
    private String ownerName;

    @Column(name = "owner_role", length = 128)
    private String ownerRole;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 64)
    private RectificationStatus status = RectificationStatus.OPEN;

    @Column(name = "completion_notes", columnDefinition = "text")
    private String completionNotes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "completion_evidence_refs", columnDefinition = "jsonb")
    private List<Map<String, Object>> completionEvidenceRefs;

    @Column(name = "verified_by", length = 255)
    private String verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verification_notes", columnDefinition = "text")
    private String verificationNotes;

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
        if (actionId == null) {
            actionId = UUID.randomUUID();
        }
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getActionId() { return actionId; }
    public void setActionId(UUID actionId) { this.actionId = actionId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public FacilityEntity getFacility() { return facility; }
    public void setFacility(FacilityEntity facility) { this.facility = facility; }
    public InspectionFindingEntity getInspectionFinding() { return inspectionFinding; }
    public void setInspectionFinding(InspectionFindingEntity inspectionFinding) { this.inspectionFinding = inspectionFinding; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getOwnerRole() { return ownerRole; }
    public void setOwnerRole(String ownerRole) { this.ownerRole = ownerRole; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public RectificationStatus getStatus() { return status; }
    public void setStatus(RectificationStatus status) { this.status = status; }
    public String getCompletionNotes() { return completionNotes; }
    public void setCompletionNotes(String completionNotes) { this.completionNotes = completionNotes; }
    public List<Map<String, Object>> getCompletionEvidenceRefs() { return completionEvidenceRefs; }
    public void setCompletionEvidenceRefs(List<Map<String, Object>> completionEvidenceRefs) { this.completionEvidenceRefs = completionEvidenceRefs; }
    public String getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(String verifiedBy) { this.verifiedBy = verifiedBy; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
    public String getVerificationNotes() { return verificationNotes; }
    public void setVerificationNotes(String verificationNotes) { this.verificationNotes = verificationNotes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
