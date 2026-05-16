package zw.gov.mohcc.impilo.msikaapps.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ma_activation_requests")
public class ActivationRequestEntity {
    @Id
    private String id = UUID.randomUUID().toString();
    @Column(name = "item_id", nullable = false) private String itemId;
    @Column(name = "requester_actor_id", nullable = false) private String requesterActorId;
    @Column(name = "requester_tenant_id", nullable = false) private String requesterTenantId;
    @Column(name = "requester_facility_id") private String requesterFacilityId;
    @Column(name = "requester_workspace_id") private String requesterWorkspaceId;
    @Column(name = "requester_programme_id") private String requesterProgrammeId;
    @Column(name = "purpose_of_use", nullable = false) private String purposeOfUse;
    @Column(nullable = false, columnDefinition = "TEXT") private String justification;
    @Column(name = "requested_configuration_json", columnDefinition = "TEXT") private String requestedConfigurationJson;
    @Column(nullable = false) private String status = "REQUESTED";
    @Column(name = "reviewers_json", columnDefinition = "TEXT") private String reviewersJson;
    @Column(name = "decided_at") private OffsetDateTime decidedAt;
    @Column(name = "decision_reason", columnDefinition = "TEXT") private String decisionReason;
    @Column(name = "correlation_id", nullable = false) private String correlationId;
    @Column(name = "pod_id", nullable = false) private String podId;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate void touch() { this.updatedAt = OffsetDateTime.now(); }

    public String getId() { return id; } public void setId(String s) { this.id = s; }
    public String getItemId() { return itemId; } public void setItemId(String s) { this.itemId = s; }
    public String getRequesterActorId() { return requesterActorId; } public void setRequesterActorId(String s) { this.requesterActorId = s; }
    public String getRequesterTenantId() { return requesterTenantId; } public void setRequesterTenantId(String s) { this.requesterTenantId = s; }
    public String getRequesterFacilityId() { return requesterFacilityId; } public void setRequesterFacilityId(String s) { this.requesterFacilityId = s; }
    public String getRequesterWorkspaceId() { return requesterWorkspaceId; } public void setRequesterWorkspaceId(String s) { this.requesterWorkspaceId = s; }
    public String getRequesterProgrammeId() { return requesterProgrammeId; } public void setRequesterProgrammeId(String s) { this.requesterProgrammeId = s; }
    public String getPurposeOfUse() { return purposeOfUse; } public void setPurposeOfUse(String s) { this.purposeOfUse = s; }
    public String getJustification() { return justification; } public void setJustification(String s) { this.justification = s; }
    public String getRequestedConfigurationJson() { return requestedConfigurationJson; } public void setRequestedConfigurationJson(String s) { this.requestedConfigurationJson = s; }
    public String getStatus() { return status; } public void setStatus(String s) { this.status = s; }
    public String getReviewersJson() { return reviewersJson; } public void setReviewersJson(String s) { this.reviewersJson = s; }
    public OffsetDateTime getDecidedAt() { return decidedAt; } public void setDecidedAt(OffsetDateTime t) { this.decidedAt = t; }
    public String getDecisionReason() { return decisionReason; } public void setDecisionReason(String s) { this.decisionReason = s; }
    public String getCorrelationId() { return correlationId; } public void setCorrelationId(String s) { this.correlationId = s; }
    public String getPodId() { return podId; } public void setPodId(String s) { this.podId = s; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime t) { this.createdAt = t; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(OffsetDateTime t) { this.updatedAt = t; }
}
