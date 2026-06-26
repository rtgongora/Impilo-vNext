package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A request to set up & run a learning provider/academy (V024), kind-aware. */
@Entity
@Table(name = "lrn_learning_space_application")
public class LearningSpaceApplicationEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "provider_kind", nullable = false, length = 32)
    private String providerKind;

    @Column(name = "requester_subject_type", length = 64)
    private String requesterSubjectType;

    @Column(name = "requester_subject_id", length = 255)
    private String requesterSubjectId;

    @Column(name = "requested_name", nullable = false, length = 512)
    private String requestedName;

    @Column(name = "target_identity_ref", length = 255)
    private String targetIdentityRef;

    @Column(name = "disciplines_json", columnDefinition = "TEXT")
    private String disciplinesJson;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "SUBMITTED";

    @Column(name = "decision_by", length = 255)
    private String decisionBy;

    @Column(name = "decision_at")
    private OffsetDateTime decisionAt;

    @Column(name = "decision_notes", columnDefinition = "TEXT")
    private String decisionNotes;

    @Column(name = "provisioned_provider_id")
    private UUID provisionedProviderId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getProviderKind() { return providerKind; }
    public void setProviderKind(String providerKind) { this.providerKind = providerKind; }
    public String getRequesterSubjectType() { return requesterSubjectType; }
    public void setRequesterSubjectType(String requesterSubjectType) { this.requesterSubjectType = requesterSubjectType; }
    public String getRequesterSubjectId() { return requesterSubjectId; }
    public void setRequesterSubjectId(String requesterSubjectId) { this.requesterSubjectId = requesterSubjectId; }
    public String getRequestedName() { return requestedName; }
    public void setRequestedName(String requestedName) { this.requestedName = requestedName; }
    public String getTargetIdentityRef() { return targetIdentityRef; }
    public void setTargetIdentityRef(String targetIdentityRef) { this.targetIdentityRef = targetIdentityRef; }
    public String getDisciplinesJson() { return disciplinesJson; }
    public void setDisciplinesJson(String disciplinesJson) { this.disciplinesJson = disciplinesJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDecisionBy() { return decisionBy; }
    public void setDecisionBy(String decisionBy) { this.decisionBy = decisionBy; }
    public OffsetDateTime getDecisionAt() { return decisionAt; }
    public void setDecisionAt(OffsetDateTime decisionAt) { this.decisionAt = decisionAt; }
    public String getDecisionNotes() { return decisionNotes; }
    public void setDecisionNotes(String decisionNotes) { this.decisionNotes = decisionNotes; }
    public UUID getProvisionedProviderId() { return provisionedProviderId; }
    public void setProvisionedProviderId(UUID provisionedProviderId) { this.provisionedProviderId = provisionedProviderId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
