package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** An advance care directive. See V106__structured_history.sql. */
@Entity
@Table(name = "pct_advance_directives")
public class AdvanceDirectiveEntity {

    @Id
    @Column(name = "directive_id")
    private UUID directiveId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "subject_cpid")
    private String subjectCpid;

    @Column(name = "directive_type")
    private String directiveType;

    @Column(name = "status")
    private String status;

    @Column(name = "summary")
    private String summary;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "reviewed_on")
    private LocalDate reviewedOn;

    @Column(name = "recorded_by")
    private String recordedBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (directiveId == null) directiveId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getDirectiveId() { return directiveId; }
    public void setDirectiveId(UUID v) { this.directiveId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { this.subjectCpid = v; }
    public String getDirectiveType() { return directiveType; }
    public void setDirectiveType(String v) { this.directiveType = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getSummary() { return summary; }
    public void setSummary(String v) { this.summary = v; }
    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID v) { this.documentId = v; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate v) { this.effectiveFrom = v; }
    public LocalDate getReviewedOn() { return reviewedOn; }
    public void setReviewedOn(LocalDate v) { this.reviewedOn = v; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String v) { this.recordedBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
