package zw.gov.mohcc.impilo.abis.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A single candidate in a duplicate investigation — one suspected-duplicate subject the
 * matcher-engine returned above the dedup/identify threshold, with its similarity score.
 * {@code candidateSubjectRef} is opaque — never a Health ID.
 */
@Entity
@Table(name = "duplicate_investigation", schema = "abis")
public class DuplicateInvestigationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private AdjudicationCaseEntity adjudicationCase;

    @Column(name = "candidate_subject_ref", nullable = false, length = 128)
    private String candidateSubjectRef;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "summary", length = 256)
    private String summary;

    @Column(name = "rank", nullable = false)
    private int rank;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public AdjudicationCaseEntity getAdjudicationCase() { return adjudicationCase; }
    public void setAdjudicationCase(AdjudicationCaseEntity adjudicationCase) { this.adjudicationCase = adjudicationCase; }

    public String getCandidateSubjectRef() { return candidateSubjectRef; }
    public void setCandidateSubjectRef(String candidateSubjectRef) { this.candidateSubjectRef = candidateSubjectRef; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public Instant getCreatedAt() { return createdAt; }
}
