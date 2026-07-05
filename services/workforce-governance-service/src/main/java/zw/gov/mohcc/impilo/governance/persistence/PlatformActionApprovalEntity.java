package zw.gov.mohcc.impilo.governance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * A single approver's decision against an access request under the two-person
 * (multi-party) approval rule. The UNIQUE (access_request_id, approver_user_id)
 * constraint guarantees each approver decides at most once, so the required N
 * approvals are provably from N distinct people. The initiator-cannot-approve
 * rule is enforced in {@code TwoPersonApprovalService}.
 */
@Entity
@Table(name = "wgv_platform_action_approval",
        uniqueConstraints = @UniqueConstraint(name = "uq_wgv_platform_action_approver",
                columnNames = {"access_request_id", "approver_user_id"}))
public class PlatformActionApprovalEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "access_request_id", nullable = false)
    private UUID accessRequestId;

    @Column(name = "approver_user_id", nullable = false)
    private String approverUserId;

    @Column(name = "approver_role", length = 128)
    private String approverRole;

    @Column(name = "decision", nullable = false, length = 32)
    private String decision = "APPROVE";

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (decidedAt == null) decidedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAccessRequestId() { return accessRequestId; }
    public void setAccessRequestId(UUID accessRequestId) { this.accessRequestId = accessRequestId; }
    public String getApproverUserId() { return approverUserId; }
    public void setApproverUserId(String approverUserId) { this.approverUserId = approverUserId; }
    public String getApproverRole() { return approverRole; }
    public void setApproverRole(String approverRole) { this.approverRole = approverRole; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
