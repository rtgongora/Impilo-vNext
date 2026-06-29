package zw.gov.mohcc.impilo.guidance.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A Nompilo AI→human handoff request and its lifecycle (G-KH-05/06).
 * Lifecycle: QUEUED → ACCEPTED → (ESCALATED) → CLOSED (CANCELLED is also terminal).
 */
@Entity
@Table(name = "nompilo_handoff", schema = "guidance")
public class NompiloHandoffEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false) private String tenantId;
    @Column(name = "transaction_id") private String transactionId;
    @Column(name = "subject_id") private String subjectId;
    @Column(name = "actor_id") private String actorId;
    @Column(columnDefinition = "TEXT") private String reason;
    @Column(nullable = false, length = 16) private String priority = "MEDIUM";
    @Column(nullable = false, length = 32) private String destination = "CARE_TEAM";
    @Column(name = "flow_id") private String flowId;
    @Column(name = "context_json", columnDefinition = "jsonb") private String contextJson;
    @Column(nullable = false, length = 16) private String status = "QUEUED";
    @Column(name = "assigned_to") private String assignedTo;
    @Column(name = "close_note", columnDefinition = "TEXT") private String closeNote;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
    @Column(name = "accepted_at") private OffsetDateTime acceptedAt;
    @Column(name = "closed_at") private OffsetDateTime closedAt;

    public NompiloHandoffEntity() {}

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String v) { this.tenantId = v; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String v) { this.transactionId = v; }
    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String v) { this.subjectId = v; }
    public String getActorId() { return actorId; }
    public void setActorId(String v) { this.actorId = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public String getPriority() { return priority; }
    public void setPriority(String v) { this.priority = v; }
    public String getDestination() { return destination; }
    public void setDestination(String v) { this.destination = v; }
    public String getFlowId() { return flowId; }
    public void setFlowId(String v) { this.flowId = v; }
    public String getContextJson() { return contextJson; }
    public void setContextJson(String v) { this.contextJson = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String v) { this.assignedTo = v; }
    public String getCloseNote() { return closeNote; }
    public void setCloseNote(String v) { this.closeNote = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(OffsetDateTime v) { this.acceptedAt = v; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(OffsetDateTime v) { this.closedAt = v; }
}
