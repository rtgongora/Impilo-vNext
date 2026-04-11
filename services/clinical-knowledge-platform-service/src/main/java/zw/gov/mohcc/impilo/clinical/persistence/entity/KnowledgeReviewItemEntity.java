package zw.gov.mohcc.impilo.clinical.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "clinical", name = "knowledge_review_items")
public class KnowledgeReviewItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "extraction_job_id")
    private UUID extractionJobId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "proposed_payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode proposedPayload;

    @Column(name = "review_status", nullable = false, length = 32)
    private String reviewStatus = "PROPOSED";

    @Column(name = "reviewer_notes", columnDefinition = "TEXT")
    private String reviewerNotes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "decided_at")
    private Instant decidedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getExtractionJobId() {
        return extractionJobId;
    }

    public void setExtractionJobId(UUID extractionJobId) {
        this.extractionJobId = extractionJobId;
    }

    public JsonNode getProposedPayload() {
        return proposedPayload;
    }

    public void setProposedPayload(JsonNode proposedPayload) {
        this.proposedPayload = proposedPayload;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public String getReviewerNotes() {
        return reviewerNotes;
    }

    public void setReviewerNotes(String reviewerNotes) {
        this.reviewerNotes = reviewerNotes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }
}
