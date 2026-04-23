package zw.gov.mohcc.impilo.mushex.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mushex_reversal_records")
public class ReversalRecordEntity {

    @Id
    @Column(name = "reversal_id", length = 26)
    private String reversalId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "reversal_code", nullable = false, length = 64)
    private String reversalCode;

    @Column(name = "original_txn_ref", nullable = false, length = 128)
    private String originalTxnRef;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "reversal_status", nullable = false, length = 30)
    private String reversalStatus = "INITIATED";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public String getReversalId() { return reversalId; }
    public void setReversalId(String reversalId) { this.reversalId = reversalId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getReversalCode() { return reversalCode; }
    public void setReversalCode(String reversalCode) { this.reversalCode = reversalCode; }
    public String getOriginalTxnRef() { return originalTxnRef; }
    public void setOriginalTxnRef(String originalTxnRef) { this.originalTxnRef = originalTxnRef; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getReversalStatus() { return reversalStatus; }
    public void setReversalStatus(String reversalStatus) { this.reversalStatus = reversalStatus; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
