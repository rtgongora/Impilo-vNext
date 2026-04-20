package zw.gov.mohcc.impilo.oros.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;
import zw.gov.mohcc.impilo.oros.domain.ResultKind;

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

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
