package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "external_council_review", schema = "tuso")
public class ExternalCouncilReviewEntity {

    @Id
    @Column(name = "review_id", nullable = false, updatable = false)
    private UUID reviewId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "reviewing_authority", nullable = false, length = 128)
    private String reviewingAuthority;

    @Column(name = "reference_number", length = 128)
    private String referenceNumber;

    @Column(name = "submitted_date")
    private LocalDate submittedDate;

    @Column(name = "decision_date")
    private LocalDate decisionDate;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "PENDING";

    @Column(name = "conditions", columnDefinition = "text")
    private String conditions;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "evidence_document_id")
    private UUID evidenceDocumentId;

    @Column(name = "recorded_by", nullable = false, length = 255)
    private String recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_provenance", columnDefinition = "jsonb")
    private Map<String, Object> sourceProvenance;

    @PrePersist
    void onCreate() {
        if (reviewId == null) {
            reviewId = UUID.randomUUID();
        }
        if (recordedAt == null) {
            recordedAt = Instant.now();
        }
    }

    public UUID getReviewId() { return reviewId; }
    public void setReviewId(UUID reviewId) { this.reviewId = reviewId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }
    public String getReviewingAuthority() { return reviewingAuthority; }
    public void setReviewingAuthority(String reviewingAuthority) { this.reviewingAuthority = reviewingAuthority; }
    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }
    public LocalDate getSubmittedDate() { return submittedDate; }
    public void setSubmittedDate(LocalDate submittedDate) { this.submittedDate = submittedDate; }
    public LocalDate getDecisionDate() { return decisionDate; }
    public void setDecisionDate(LocalDate decisionDate) { this.decisionDate = decisionDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getConditions() { return conditions; }
    public void setConditions(String conditions) { this.conditions = conditions; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public UUID getEvidenceDocumentId() { return evidenceDocumentId; }
    public void setEvidenceDocumentId(UUID evidenceDocumentId) { this.evidenceDocumentId = evidenceDocumentId; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }
    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }
    public Map<String, Object> getSourceProvenance() { return sourceProvenance; }
    public void setSourceProvenance(Map<String, Object> sourceProvenance) { this.sourceProvenance = sourceProvenance; }
}
