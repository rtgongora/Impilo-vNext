package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import zw.gov.mohcc.impilo.vito.core.ClientStewardshipActionType;
import zw.gov.mohcc.impilo.vito.core.ClientStewardshipStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "client_stewardship_action", schema = "vito")
public class ClientStewardshipActionEntity {

    @Id
    @Column(name = "action_id", nullable = false, updatable = false)
    private UUID actionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_health_id", nullable = false)
    private UUID clientHealthId;

    @Column(name = "related_registration_id")
    private UUID relatedRegistrationId;

    @Column(name = "related_merge_case_id")
    private UUID relatedMergeCaseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ClientStewardshipActionType actionType;

    @Column(name = "owner")
    private String owner;

    @Column(name = "due_date")
    private OffsetDateTime dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ClientStewardshipStatus status = ClientStewardshipStatus.OPEN;

    @Column(name = "completion_notes", columnDefinition = "TEXT")
    private String completionNotes;

    @Column(name = "verified_by")
    private String verifiedBy;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (actionId == null) {
            actionId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getActionId() { return actionId; }
    public void setActionId(UUID actionId) { this.actionId = actionId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getClientHealthId() { return clientHealthId; }
    public void setClientHealthId(UUID clientHealthId) { this.clientHealthId = clientHealthId; }
    public UUID getRelatedRegistrationId() { return relatedRegistrationId; }
    public void setRelatedRegistrationId(UUID relatedRegistrationId) { this.relatedRegistrationId = relatedRegistrationId; }
    public UUID getRelatedMergeCaseId() { return relatedMergeCaseId; }
    public void setRelatedMergeCaseId(UUID relatedMergeCaseId) { this.relatedMergeCaseId = relatedMergeCaseId; }
    public ClientStewardshipActionType getActionType() { return actionType; }
    public void setActionType(ClientStewardshipActionType actionType) { this.actionType = actionType; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public OffsetDateTime getDueDate() { return dueDate; }
    public void setDueDate(OffsetDateTime dueDate) { this.dueDate = dueDate; }
    public ClientStewardshipStatus getStatus() { return status; }
    public void setStatus(ClientStewardshipStatus status) { this.status = status; }
    public String getCompletionNotes() { return completionNotes; }
    public void setCompletionNotes(String completionNotes) { this.completionNotes = completionNotes; }
    public String getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(String verifiedBy) { this.verifiedBy = verifiedBy; }
    public OffsetDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(OffsetDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
