package zw.gov.mohcc.impilo.wallet.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A shareable "help pay my bill" contribution request (Mushe social funding).
 *
 * <p>With origin {@code CAMPAIGN} (crowdfunding, case layer: Daidzai) the
 * {@code beneficiaryWalletId} points at a dedicated escrow wallet
 * (ownerType={@code CROWDFUND_ESCROW}, ownerRef={@code campaignRef}) and
 * {@code beneficiaryTargetWalletId} at the real beneficiary; funds only move
 * from escrow to beneficiary through an explicit release.</p>
 */
@Entity
@Table(name = "bill_contribution_requests", schema = "mushe")
public class BillContributionRequestEntity {

    public static final String ORIGIN_BILL = "BILL";
    public static final String ORIGIN_CAMPAIGN = "CAMPAIGN";

    /** Cancelled but at least one contribution is still awaiting refund (escrow shortfall). */
    public static final String STATUS_CANCELLED_REFUNDING = "CANCELLED_REFUNDING";
    /** Cancelled and every contribution has a terminal refund outcome. */
    public static final String STATUS_CANCELLED_SETTLED = "CANCELLED_SETTLED";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "share_token", nullable = false, unique = true, length = 48)
    private String shareToken;

    @Column(name = "beneficiary_wallet_id", nullable = false)
    private UUID beneficiaryWalletId;

    @Column(name = "origin", nullable = false, length = 24)
    private String origin = ORIGIN_BILL;

    @Column(name = "campaign_ref")
    private UUID campaignRef;

    @Column(name = "beneficiary_target_wallet_id")
    private UUID beneficiaryTargetWalletId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "bill_ref", length = 120)
    private String billRef;

    @Column(name = "target_amount", precision = 14, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "raised_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal raisedAmount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "status", nullable = false, length = 24)
    private String status = "OPEN";

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getShareToken() { return shareToken; }
    public void setShareToken(String shareToken) { this.shareToken = shareToken; }
    public UUID getBeneficiaryWalletId() { return beneficiaryWalletId; }
    public void setBeneficiaryWalletId(UUID beneficiaryWalletId) { this.beneficiaryWalletId = beneficiaryWalletId; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public UUID getCampaignRef() { return campaignRef; }
    public void setCampaignRef(UUID campaignRef) { this.campaignRef = campaignRef; }
    public UUID getBeneficiaryTargetWalletId() { return beneficiaryTargetWalletId; }
    public void setBeneficiaryTargetWalletId(UUID beneficiaryTargetWalletId) { this.beneficiaryTargetWalletId = beneficiaryTargetWalletId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBillRef() { return billRef; }
    public void setBillRef(String billRef) { this.billRef = billRef; }
    public BigDecimal getTargetAmount() { return targetAmount; }
    public void setTargetAmount(BigDecimal targetAmount) { this.targetAmount = targetAmount; }
    public BigDecimal getRaisedAmount() { return raisedAmount; }
    public void setRaisedAmount(BigDecimal raisedAmount) { this.raisedAmount = raisedAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
