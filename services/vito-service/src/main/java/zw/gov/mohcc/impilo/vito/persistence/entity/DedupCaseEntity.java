package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dedup_case", schema = "vito")
public class DedupCaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "candidate_a", nullable = false)
    private UUID candidateA;

    @Column(name = "candidate_b", nullable = false)
    private UUID candidateB;

    @Column(name = "match_score")
    private BigDecimal matchScore;

    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "risk")
    private String risk;

    @Column(name = "reasons", columnDefinition = "jsonb")
    private String reasons;

    @Column(name = "assigned_to")
    private String assignedTo;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getCandidateA() { return candidateA; }
    public void setCandidateA(UUID candidateA) { this.candidateA = candidateA; }
    public UUID getCandidateB() { return candidateB; }
    public void setCandidateB(UUID candidateB) { this.candidateB = candidateB; }
    public BigDecimal getMatchScore() { return matchScore; }
    public void setMatchScore(BigDecimal matchScore) { this.matchScore = matchScore; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRisk() { return risk; }
    public void setRisk(String risk) { this.risk = risk; }
    public String getReasons() { return reasons; }
    public void setReasons(String reasons) { this.reasons = reasons; }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(OffsetDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
