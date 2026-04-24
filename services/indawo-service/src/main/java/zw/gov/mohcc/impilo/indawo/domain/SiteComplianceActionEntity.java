package zw.gov.mohcc.impilo.indawo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ind_site_compliance_actions")
public class SiteComplianceActionEntity {

    @Id
    @Column(name = "action_id", nullable = false)
    private UUID actionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "finding_id")
    private UUID findingId;

    @Column(name = "action_type", nullable = false, length = 64)
    private String actionType;

    @Column(name = "owner_ref", length = 255)
    private String ownerRef;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "OPEN";

    @Column(name = "completion_notes", columnDefinition = "TEXT")
    private String completionNotes;

    @Column(name = "verified_by", length = 255)
    private String verifiedBy;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected SiteComplianceActionEntity() {}

    public SiteComplianceActionEntity(UUID actionId, UUID tenantId, UUID siteId, String actionType) {
        this.actionId = actionId;
        this.tenantId = tenantId;
        this.siteId = siteId;
        this.actionType = actionType;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getActionId() { return actionId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getSiteId() { return siteId; }
    public UUID getFindingId() { return findingId; }
    public void setFindingId(UUID findingId) { this.findingId = findingId; touch(null); }
    public String getActionType() { return actionType; }
    public String getOwnerRef() { return ownerRef; }
    public void setOwnerRef(String ownerRef) { this.ownerRef = ownerRef; touch(null); }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; touch(null); }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; touch(null); }
    public String getCompletionNotes() { return completionNotes; }
    public void setCompletionNotes(String completionNotes) { this.completionNotes = completionNotes; touch(null); }
    public String getVerifiedBy() { return verifiedBy; }
    public OffsetDateTime getVerifiedAt() { return verifiedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void verify(String actorRef, String notes) {
        this.status = "VERIFIED";
        this.verifiedBy = actorRef;
        this.verifiedAt = OffsetDateTime.now();
        this.completionNotes = notes;
        touch(actorRef);
    }

    private void touch(String actorRef) {
        this.updatedAt = OffsetDateTime.now();
    }
}

