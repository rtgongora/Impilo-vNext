package zw.gov.mohcc.impilo.surv.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One recorded step in a case investigation.
 *
 * <p>A history rather than a column, because a classification moves suspected → probable →
 * confirmed as evidence arrives, and a retrospective asks "when did we know" — which a single
 * overwritten value cannot answer. The current values are mirrored onto the case for line lists,
 * which read hundreds of rows and must not fold a history per row.
 */
@Entity
@Table(name = "case_updates", schema = "surv")
public class CaseUpdateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "classification")
    private String classification;

    @Column(name = "outcome")
    private String outcome;

    @Column(name = "lab_result")
    private String labResult;

    @Column(name = "notes")
    private String notes;

    @Column(name = "recorded_by", nullable = false)
    private String recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    @PrePersist
    void onCreate() {
        if (recordedAt == null) recordedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public Long getCaseId() { return caseId; }
    public void setCaseId(Long caseId) { this.caseId = caseId; }
    public String getClassification() { return classification; }
    public void setClassification(String classification) { this.classification = classification; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getLabResult() { return labResult; }
    public void setLabResult(String labResult) { this.labResult = labResult; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime recordedAt) { this.recordedAt = recordedAt; }
}
