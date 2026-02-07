package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "wallet_journal", schema = "vito")
public class WalletJournalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "transaction_ref", nullable = false)
    private String transactionRef;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Column(name = "entry_type", nullable = false)
    private String entryType; // DEBIT, CREDIT

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "running_balance", nullable = false)
    private BigDecimal runningBalance;

    @Column(name = "counterparty_type", nullable = false)
    private String counterpartyType;

    @Column(name = "counterparty_ref")
    private String counterpartyRef;

    @Column(name = "description")
    private String description;

    @Column(name = "offline_signature", columnDefinition = "TEXT")
    private String offlineSignature;

    @Column(name = "offline_timestamp")
    private OffsetDateTime offlineTimestamp;

    @Column(name = "reconciled", nullable = false)
    private boolean reconciled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }
    public Long getWalletId() { return walletId; }
    public void setWalletId(Long walletId) { this.walletId = walletId; }
    public String getEntryType() { return entryType; }
    public void setEntryType(String entryType) { this.entryType = entryType; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getRunningBalance() { return runningBalance; }
    public void setRunningBalance(BigDecimal runningBalance) { this.runningBalance = runningBalance; }
    public String getCounterpartyType() { return counterpartyType; }
    public void setCounterpartyType(String counterpartyType) { this.counterpartyType = counterpartyType; }
    public String getCounterpartyRef() { return counterpartyRef; }
    public void setCounterpartyRef(String counterpartyRef) { this.counterpartyRef = counterpartyRef; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOfflineSignature() { return offlineSignature; }
    public void setOfflineSignature(String offlineSignature) { this.offlineSignature = offlineSignature; }
    public OffsetDateTime getOfflineTimestamp() { return offlineTimestamp; }
    public void setOfflineTimestamp(OffsetDateTime offlineTimestamp) { this.offlineTimestamp = offlineTimestamp; }
    public boolean isReconciled() { return reconciled; }
    public void setReconciled(boolean reconciled) { this.reconciled = reconciled; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
