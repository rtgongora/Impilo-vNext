package zw.gov.mohcc.impilo.indawo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ind_site_inspection_findings")
public class SiteInspectionFindingEntity {

    @Id
    @Column(name = "finding_id", nullable = false)
    private UUID findingId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "inspection_id", nullable = false)
    private UUID inspectionId;

    @Column(name = "checklist_item_id")
    private UUID checklistItemId;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "severity", length = 16)
    private String severity;

    @Column(name = "comments", columnDefinition = "TEXT")
    private String comments;

    @Column(name = "evidence_refs", columnDefinition = "JSONB")
    private String evidenceRefsJson;

    @Column(name = "rectification_deadline")
    private LocalDate rectificationDeadline;

    @Column(name = "rectification_status", nullable = false, length = 32)
    private String rectificationStatus = "PENDING";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /**
     * Set true when the owning inspection is signed/finalised. A signed finding
     * is append-only (SJ4): the DB trigger (V012) rejects UPDATE/DELETE, so
     * corrections must be new compliance actions, never edits.
     */
    @Column(name = "signed_flag", nullable = false)
    private boolean signedFlag = false;

    protected SiteInspectionFindingEntity() {}

    public SiteInspectionFindingEntity(UUID findingId, UUID tenantId, UUID inspectionId, String status) {
        this.findingId = findingId;
        this.tenantId = tenantId;
        this.inspectionId = inspectionId;
        this.status = status;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getFindingId() { return findingId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getInspectionId() { return inspectionId; }
    public UUID getChecklistItemId() { return checklistItemId; }
    public void setChecklistItemId(UUID checklistItemId) { this.checklistItemId = checklistItemId; }
    public String getStatus() { return status; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
    public String getEvidenceRefsJson() { return evidenceRefsJson; }
    public void setEvidenceRefsJson(String evidenceRefsJson) { this.evidenceRefsJson = evidenceRefsJson; }
    public LocalDate getRectificationDeadline() { return rectificationDeadline; }
    public void setRectificationDeadline(LocalDate rectificationDeadline) { this.rectificationDeadline = rectificationDeadline; }
    public String getRectificationStatus() { return rectificationStatus; }
    public void setRectificationStatus(String rectificationStatus) { this.rectificationStatus = rectificationStatus; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public boolean isSignedFlag() { return signedFlag; }
    public void setSignedFlag(boolean signedFlag) { this.signedFlag = signedFlag; }
}

