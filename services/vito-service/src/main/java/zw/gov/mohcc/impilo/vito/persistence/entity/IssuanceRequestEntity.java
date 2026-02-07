package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.*;
import zw.gov.mohcc.impilo.vito.core.IssuanceChannel;
import zw.gov.mohcc.impilo.vito.core.IssuanceState;
import zw.gov.mohcc.impilo.vito.core.IssuanceType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "issuance_request", schema = "vito")
public class IssuanceRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "health_id", nullable = false)
    private UUID healthId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private IssuanceType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private IssuanceChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private IssuanceState state = IssuanceState.SUBMITTED;

    @Column(name = "risk_score")
    private int riskScore = 0;

    @Column(name = "assigned_to")
    private String assignedTo;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "impilo_id_issued")
    private String impiloIdIssued;

    @Column(name = "card_id")
    private Long cardId;

    @Column(name = "proofing_checklist", columnDefinition = "jsonb")
    private String proofingChecklist;

    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "proofing_started_at")
    private OffsetDateTime proofingStartedAt;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "issued_at")
    private OffsetDateTime issuedAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "rejected_at")
    private OffsetDateTime rejectedAt;

    @Column(name = "expired_at")
    private OffsetDateTime expiredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        submittedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getHealthId() { return healthId; }
    public void setHealthId(UUID healthId) { this.healthId = healthId; }
    public IssuanceType getType() { return type; }
    public void setType(IssuanceType type) { this.type = type; }
    public IssuanceChannel getChannel() { return channel; }
    public void setChannel(IssuanceChannel channel) { this.channel = channel; }
    public IssuanceState getState() { return state; }
    public void setState(IssuanceState state) { this.state = state; }
    public int getRiskScore() { return riskScore; }
    public void setRiskScore(int riskScore) { this.riskScore = riskScore; }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public String getImpiloIdIssued() { return impiloIdIssued; }
    public void setImpiloIdIssued(String impiloIdIssued) { this.impiloIdIssued = impiloIdIssued; }
    public Long getCardId() { return cardId; }
    public void setCardId(Long cardId) { this.cardId = cardId; }
    public String getProofingChecklist() { return proofingChecklist; }
    public void setProofingChecklist(String proofingChecklist) { this.proofingChecklist = proofingChecklist; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }
    public OffsetDateTime getProofingStartedAt() { return proofingStartedAt; }
    public void setProofingStartedAt(OffsetDateTime proofingStartedAt) { this.proofingStartedAt = proofingStartedAt; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(OffsetDateTime approvedAt) { this.approvedAt = approvedAt; }
    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(OffsetDateTime issuedAt) { this.issuedAt = issuedAt; }
    public OffsetDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(OffsetDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
    public OffsetDateTime getRejectedAt() { return rejectedAt; }
    public void setRejectedAt(OffsetDateTime rejectedAt) { this.rejectedAt = rejectedAt; }
    public OffsetDateTime getExpiredAt() { return expiredAt; }
    public void setExpiredAt(OffsetDateTime expiredAt) { this.expiredAt = expiredAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
