package zw.gov.mohcc.impilo.msikaflow.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.msikaflow.domain.AnomalyFindingStatus;
import zw.gov.mohcc.impilo.msikaflow.domain.AnomalyFindingType;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * OF-B29 (Wave OF-D) — one accountable anomaly/fairness finding
 * ({@code mf_anomaly_findings}, Vol II §13.7/§21). Coded and PII-free:
 * {@code detailsJson} carries ids, counts and thresholds only. Dedup rides
 * the {@code (tenant_id, dedupe_key)} unique index so sweeps re-run without
 * re-flagging the same fact.
 */
@Entity
@Table(name = "mf_anomaly_findings")
public class AnomalyFindingEntity {

    @Id
    @Column(name = "finding_id", nullable = false, length = 26)
    private String findingId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "finding_type", nullable = false, length = 48)
    private AnomalyFindingType findingType;

    @Column(name = "severity", nullable = false, length = 16)
    private String severity;

    @Column(name = "subject_type", nullable = false, length = 32)
    private String subjectType;

    @Column(name = "subject_ref", nullable = false, length = 128)
    private String subjectRef;

    @Column(name = "dedupe_key", nullable = false, length = 160)
    private String dedupeKey;

    @Column(name = "window_start")
    private OffsetDateTime windowStart;

    @Column(name = "window_end")
    private OffsetDateTime windowEnd;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details_json", columnDefinition = "jsonb")
    private String detailsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AnomalyFindingStatus status = AnomalyFindingStatus.OPEN;

    @Column(name = "reviewed_by", length = 128)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "review_reason", length = 512)
    private String reviewReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public String getFindingId() { return findingId; }
    public void setFindingId(String findingId) { this.findingId = findingId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public AnomalyFindingType getFindingType() { return findingType; }
    public void setFindingType(AnomalyFindingType findingType) { this.findingType = findingType; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }

    public String getSubjectRef() { return subjectRef; }
    public void setSubjectRef(String subjectRef) { this.subjectRef = subjectRef; }

    public String getDedupeKey() { return dedupeKey; }
    public void setDedupeKey(String dedupeKey) { this.dedupeKey = dedupeKey; }

    public OffsetDateTime getWindowStart() { return windowStart; }
    public void setWindowStart(OffsetDateTime windowStart) { this.windowStart = windowStart; }

    public OffsetDateTime getWindowEnd() { return windowEnd; }
    public void setWindowEnd(OffsetDateTime windowEnd) { this.windowEnd = windowEnd; }

    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }

    public AnomalyFindingStatus getStatus() { return status; }
    public void setStatus(AnomalyFindingStatus status) { this.status = status; }

    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }

    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(OffsetDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public String getReviewReason() { return reviewReason; }
    public void setReviewReason(String reviewReason) { this.reviewReason = reviewReason; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
