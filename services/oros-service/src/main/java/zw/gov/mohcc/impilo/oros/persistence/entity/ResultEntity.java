package zw.gov.mohcc.impilo.oros.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;
import zw.gov.mohcc.impilo.oros.domain.ResultKind;
import zw.gov.mohcc.impilo.oros.domain.ResultStatus;

/**
 * Stores a result captured against a clinical order.
 * Results can be lab reports, imaging studies, pharmacy dispensing records,
 * or attached documents.
 */
@Entity
@Table(name = "oros_results")
public class ResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "result_id", nullable = false)
    private UUID resultId;

    @Column(name = "order_id", nullable = false, length = 26)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private ResultKind kind;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "summary", nullable = false, columnDefinition = "jsonb")
    private String summary;

    @Column(name = "zibo_result_codes", length = 500)
    private String ziboResultCodes;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "doc_ids", columnDefinition = "jsonb")
    private String docIds;

    @Column(name = "is_critical", nullable = false)
    private boolean isCritical = false;

    @Column(name = "reported_by", nullable = false, length = 128)
    private String reportedBy;

    // ── Reporting lifecycle (V006) ───────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "report_status", length = 20)
    private ResultStatus reportStatus;

    @Column(name = "impression", columnDefinition = "TEXT")
    private String impression;

    @Column(name = "recommendations", columnDefinition = "TEXT")
    private String recommendations;

    @Column(name = "critical_reason", columnDefinition = "TEXT")
    private String criticalReason;

    @Column(name = "validated_by", length = 128)
    private String validatedBy;

    @Column(name = "released_at")
    private OffsetDateTime releasedAt;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "acknowledged_by", length = 128)
    private String acknowledgedBy;

    @Column(name = "supersedes_result_id")
    private UUID supersedesResultId;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "escalated_at")
    private OffsetDateTime escalatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public UUID getResultId() { return resultId; }
    public void setResultId(UUID resultId) { this.resultId = resultId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public ResultKind getKind() { return kind; }
    public void setKind(ResultKind kind) { this.kind = kind; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getZiboResultCodes() { return ziboResultCodes; }
    public void setZiboResultCodes(String ziboResultCodes) { this.ziboResultCodes = ziboResultCodes; }

    public String getDocIds() { return docIds; }
    public void setDocIds(String docIds) { this.docIds = docIds; }

    public boolean isCritical() { return isCritical; }
    public void setCritical(boolean critical) { isCritical = critical; }

    public String getReportedBy() { return reportedBy; }
    public void setReportedBy(String reportedBy) { this.reportedBy = reportedBy; }

    public ResultStatus getReportStatus() { return reportStatus; }
    public void setReportStatus(ResultStatus reportStatus) { this.reportStatus = reportStatus; }

    public String getImpression() { return impression; }
    public void setImpression(String impression) { this.impression = impression; }

    public String getRecommendations() { return recommendations; }
    public void setRecommendations(String recommendations) { this.recommendations = recommendations; }

    public String getCriticalReason() { return criticalReason; }
    public void setCriticalReason(String criticalReason) { this.criticalReason = criticalReason; }

    public String getValidatedBy() { return validatedBy; }
    public void setValidatedBy(String validatedBy) { this.validatedBy = validatedBy; }

    public OffsetDateTime getReleasedAt() { return releasedAt; }
    public void setReleasedAt(OffsetDateTime releasedAt) { this.releasedAt = releasedAt; }

    public OffsetDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(OffsetDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }

    public String getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(String acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }

    public UUID getSupersedesResultId() { return supersedesResultId; }
    public void setSupersedesResultId(UUID supersedesResultId) { this.supersedesResultId = supersedesResultId; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public OffsetDateTime getEscalatedAt() { return escalatedAt; }
    public void setEscalatedAt(OffsetDateTime escalatedAt) { this.escalatedAt = escalatedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
