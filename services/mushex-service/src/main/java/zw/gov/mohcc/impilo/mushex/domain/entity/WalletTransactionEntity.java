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
@Table(name = "mushex_wallet_transactions")
public class WalletTransactionEntity {

    @Id
    @Column(name = "txn_id", length = 26)
    private String txnId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "wallet_id", nullable = false, length = 26)
    private String walletId;

    @Column(name = "transaction_type", nullable = false, length = 40)
    private String transactionType;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "direction", nullable = false, length = 8)
    private String direction;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "POSTED";

    @Column(name = "reference_code", length = 64)
    private String referenceCode;

    @Column(name = "related_payment_intent_id", length = 26)
    private String relatedPaymentIntentId;

    @Column(name = "related_remittance_request_id", length = 26)
    private String relatedRemittanceRequestId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public String getTxnId() { return txnId; }
    public void setTxnId(String txnId) { this.txnId = txnId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getWalletId() { return walletId; }
    public void setWalletId(String walletId) { this.walletId = walletId; }
    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReferenceCode() { return referenceCode; }
    public void setReferenceCode(String referenceCode) { this.referenceCode = referenceCode; }
    public String getRelatedPaymentIntentId() { return relatedPaymentIntentId; }
    public void setRelatedPaymentIntentId(String relatedPaymentIntentId) { this.relatedPaymentIntentId = relatedPaymentIntentId; }
    public String getRelatedRemittanceRequestId() { return relatedRemittanceRequestId; }
    public void setRelatedRemittanceRequestId(String relatedRemittanceRequestId) { this.relatedRemittanceRequestId = relatedRemittanceRequestId; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
