package zw.gov.mohcc.impilo.wallet.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A cash-in that has been REQUESTED but not necessarily received. External
 * deposits (mobile money, bank/ZIPIT, card, remittance) stay PENDING until the
 * money's arrival is confirmed — a provider callback or a matched statement
 * line — and only confirmation credits the wallet. The ledger never goes up
 * on intention.
 */
@Entity
@Table(name = "deposit_intents", schema = "mushe")
public class DepositIntentEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** Ordinary cash-in: confirmation credits the wallet and that is all. */
    public static final String PURPOSE_WALLET_TOPUP = "WALLET_TOPUP";
    /** PSP donation to a crowdfunding escrow: confirmation also records the contribution. */
    public static final String PURPOSE_CROWDFUNDING = "CROWDFUNDING";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deposit_id", nullable = false, unique = true)
    private UUID depositId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "channel", nullable = false, length = 32)
    private String channel;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "reference_code", nullable = false, unique = true, length = 24)
    private String referenceCode;

    @Column(name = "external_ref", length = 255)
    private String externalRef;

    @Column(name = "status", nullable = false, length = 16)
    private String status = STATUS_PENDING;

    @Column(name = "purpose", nullable = false, length = 32)
    private String purpose = PURPOSE_WALLET_TOPUP;

    /** Purpose-specific anchor — for CROWDFUNDING: the contribution request's share token. */
    @Column(name = "purpose_ref", length = 64)
    private String purposeRef;

    /** Purpose-specific context (JSON) — e.g. donor name / message / anonymity for CROWDFUNDING. */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "credited_txn_id")
    private UUID creditedTxnId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "confirmed_by", length = 128)
    private String confirmedBy;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (depositId == null) depositId = UUID.randomUUID();
    }

    public Long getId() { return id; }
    public UUID getDepositId() { return depositId; }
    public void setDepositId(UUID depositId) { this.depositId = depositId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getWalletId() { return walletId; }
    public void setWalletId(UUID walletId) { this.walletId = walletId; }
    public UUID getSourceId() { return sourceId; }
    public void setSourceId(UUID sourceId) { this.sourceId = sourceId; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getReferenceCode() { return referenceCode; }
    public void setReferenceCode(String referenceCode) { this.referenceCode = referenceCode; }
    public String getExternalRef() { return externalRef; }
    public void setExternalRef(String externalRef) { this.externalRef = externalRef; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getPurposeRef() { return purposeRef; }
    public void setPurposeRef(String purposeRef) { this.purposeRef = purposeRef; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public UUID getCreditedTxnId() { return creditedTxnId; }
    public void setCreditedTxnId(UUID creditedTxnId) { this.creditedTxnId = creditedTxnId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(OffsetDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    public String getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(String confirmedBy) { this.confirmedBy = confirmedBy; }
}
