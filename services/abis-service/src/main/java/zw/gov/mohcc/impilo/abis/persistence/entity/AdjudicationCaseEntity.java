package zw.gov.mohcc.impilo.abis.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A biometric adjudication case — one reviewable item per probe subject that had candidate
 * matches (enrol-time dedup or a 1:N identify). Holds workflow state, the human decision,
 * and the dual-control merge record.
 *
 * <p><b>Governance invariant:</b> a merge is NEVER automatic. It requires
 * decision={@code CONFIRMED_DUPLICATE} plus a second reviewer ({@code dualSignOffBy})
 * distinct from {@code decidedBy}. {@code subjectRef} is opaque — never a Health ID.</p>
 */
@Entity
@Table(name = "adjudication_case", schema = "abis")
public class AdjudicationCaseEntity {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_IN_REVIEW = "IN_REVIEW";
    public static final String STATUS_RESOLVED = "RESOLVED";
    public static final String DECISION_DISTINCT = "DISTINCT";
    public static final String DECISION_CONFIRMED_DUPLICATE = "CONFIRMED_DUPLICATE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_ref", nullable = false, length = 128)
    private String subjectRef;

    @Column(name = "modality", nullable = false, length = 32)
    private String modality;

    @Column(name = "reason", nullable = false, length = 32)
    private String reason;

    @Column(name = "status", nullable = false, length = 16)
    private String status = STATUS_OPEN;

    @Column(name = "decision", length = 32)
    private String decision;

    @Column(name = "candidate_count", nullable = false)
    private int candidateCount = 0;

    @Column(name = "top_score")
    private Double topScore;

    @Column(name = "assigned_reviewer", length = 128)
    private String assignedReviewer;

    @Column(name = "decided_by", length = 128)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_reason")
    private String decisionReason;

    @Column(name = "merge_target_subject_ref", length = 128)
    private String mergeTargetSubjectRef;

    @Column(name = "dual_sign_off_by", length = 128)
    private String dualSignOffBy;

    @Column(name = "merged_at")
    private Instant mergedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "adjudicationCase", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rank ASC")
    private List<DuplicateInvestigationEntity> candidates = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void addCandidate(DuplicateInvestigationEntity candidate) {
        candidate.setAdjudicationCase(this);
        this.candidates.add(candidate);
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getSubjectRef() { return subjectRef; }
    public void setSubjectRef(String subjectRef) { this.subjectRef = subjectRef; }

    public String getModality() { return modality; }
    public void setModality(String modality) { this.modality = modality; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public int getCandidateCount() { return candidateCount; }
    public void setCandidateCount(int candidateCount) { this.candidateCount = candidateCount; }

    public Double getTopScore() { return topScore; }
    public void setTopScore(Double topScore) { this.topScore = topScore; }

    public String getAssignedReviewer() { return assignedReviewer; }
    public void setAssignedReviewer(String assignedReviewer) { this.assignedReviewer = assignedReviewer; }

    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }

    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }

    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }

    public String getMergeTargetSubjectRef() { return mergeTargetSubjectRef; }
    public void setMergeTargetSubjectRef(String mergeTargetSubjectRef) { this.mergeTargetSubjectRef = mergeTargetSubjectRef; }

    public String getDualSignOffBy() { return dualSignOffBy; }
    public void setDualSignOffBy(String dualSignOffBy) { this.dualSignOffBy = dualSignOffBy; }

    public Instant getMergedAt() { return mergedAt; }
    public void setMergedAt(Instant mergedAt) { this.mergedAt = mergedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public List<DuplicateInvestigationEntity> getCandidates() { return candidates; }
    public void setCandidates(List<DuplicateInvestigationEntity> candidates) { this.candidates = candidates; }
}
