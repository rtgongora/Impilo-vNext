package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.vito.core.ClientRegistrationType;
import zw.gov.mohcc.impilo.vito.core.ClientRegistrationWorkflowState;
import zw.gov.mohcc.impilo.vito.core.ClientVerificationState;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "client_registration", schema = "vito")
public class ClientRegistrationEntity {

    @Id
    @Column(name = "registration_id", nullable = false, updatable = false)
    private UUID registrationId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_health_id")
    private UUID clientHealthId;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_type", nullable = false)
    private ClientRegistrationType registrationType;

    @Column(name = "initiated_by", nullable = false)
    private String initiatedBy;

    @Column(name = "initiated_channel", nullable = false)
    private String initiatedChannel;

    @Column(name = "linked_facility_id")
    private Long linkedFacilityId;

    @Column(name = "linked_provider_id")
    private String linkedProviderId;

    @Column(name = "linked_service_context_id")
    private String linkedServiceContextId;

    @Column(name = "submission_date")
    private OffsetDateTime submissionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_state", nullable = false)
    private ClientRegistrationWorkflowState workflowState = ClientRegistrationWorkflowState.DRAFT;

    @Column(name = "completion_state", nullable = false)
    private String completionState = "INCOMPLETE";

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_state", nullable = false)
    private ClientVerificationState verificationState = ClientVerificationState.UNVERIFIED;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provenance", columnDefinition = "jsonb")
    private String provenance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (registrationId == null) {
            registrationId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getRegistrationId() { return registrationId; }
    public void setRegistrationId(UUID registrationId) { this.registrationId = registrationId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getClientHealthId() { return clientHealthId; }
    public void setClientHealthId(UUID clientHealthId) { this.clientHealthId = clientHealthId; }
    public ClientRegistrationType getRegistrationType() { return registrationType; }
    public void setRegistrationType(ClientRegistrationType registrationType) { this.registrationType = registrationType; }
    public String getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }
    public String getInitiatedChannel() { return initiatedChannel; }
    public void setInitiatedChannel(String initiatedChannel) { this.initiatedChannel = initiatedChannel; }
    public Long getLinkedFacilityId() { return linkedFacilityId; }
    public void setLinkedFacilityId(Long linkedFacilityId) { this.linkedFacilityId = linkedFacilityId; }
    public String getLinkedProviderId() { return linkedProviderId; }
    public void setLinkedProviderId(String linkedProviderId) { this.linkedProviderId = linkedProviderId; }
    public String getLinkedServiceContextId() { return linkedServiceContextId; }
    public void setLinkedServiceContextId(String linkedServiceContextId) { this.linkedServiceContextId = linkedServiceContextId; }
    public OffsetDateTime getSubmissionDate() { return submissionDate; }
    public void setSubmissionDate(OffsetDateTime submissionDate) { this.submissionDate = submissionDate; }
    public ClientRegistrationWorkflowState getWorkflowState() { return workflowState; }
    public void setWorkflowState(ClientRegistrationWorkflowState workflowState) { this.workflowState = workflowState; }
    public String getCompletionState() { return completionState; }
    public void setCompletionState(String completionState) { this.completionState = completionState; }
    public ClientVerificationState getVerificationState() { return verificationState; }
    public void setVerificationState(ClientVerificationState verificationState) { this.verificationState = verificationState; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getProvenance() { return provenance; }
    public void setProvenance(String provenance) { this.provenance = provenance; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
