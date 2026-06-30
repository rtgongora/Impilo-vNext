package zw.gov.mohcc.impilo.msika.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A buyer-discoverable listing over a catalog item (and optional offering).
 * Carries presentation, risk/eligibility, approval/publish lifecycle and
 * cross-service references ONLY. Orders/billing/payment/stock/clinical truth
 * stay with their owners (msika-flow / Costa / MusheX / inventory / PCT-OROS).
 */
@Entity
@Table(name = "msika_listings")
public class ListingEntity {

    @Id
    @Column(name = "listing_id", length = 26)
    private String listingId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "storefront_id", length = 26)
    private String storefrontId;

    @Column(name = "catalog_item_id", nullable = false, length = 26)
    private String catalogItemId;

    @Column(name = "offering_id", length = 26)
    private String offeringId;

    @Column(name = "seller_type", nullable = false, length = 20)
    private String sellerType;

    @Column(name = "seller_id", nullable = false, length = 128)
    private String sellerId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "summary")
    private String summary;

    @Column(name = "risk_classification", nullable = false, length = 32)
    private String riskClassification = "LOW_RISK";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "eligibility_rule", nullable = false, columnDefinition = "jsonb")
    private String eligibilityRuleJson = "{}";

    @Column(name = "clinical_route", length = 20)
    private String clinicalRoute;

    @Column(name = "charge_ref", length = 128)
    private String chargeRef;

    @Column(name = "price_display", length = 64)
    private String priceDisplay;

    @Column(name = "fulfilment_mode", length = 30)
    private String fulfilmentMode;

    @Column(name = "fulfilment_owner", nullable = false, length = 30)
    private String fulfilmentOwner = "MSIKA_FLOW";

    @Column(name = "status", nullable = false, length = 24)
    private String status = "DRAFT";

    @Column(name = "moderation_notes")
    private String moderationNotes;

    @Column(name = "approved_by", length = 128)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "fundo_ref", length = 128)
    private String fundoRef;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    private int lockVersion;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (eligibilityRuleJson == null) eligibilityRuleJson = "{}";
        if (metadataJson == null) metadataJson = "{}";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public String getListingId() { return listingId; }
    public void setListingId(String listingId) { this.listingId = listingId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getStorefrontId() { return storefrontId; }
    public void setStorefrontId(String storefrontId) { this.storefrontId = storefrontId; }
    public String getCatalogItemId() { return catalogItemId; }
    public void setCatalogItemId(String catalogItemId) { this.catalogItemId = catalogItemId; }
    public String getOfferingId() { return offeringId; }
    public void setOfferingId(String offeringId) { this.offeringId = offeringId; }
    public String getSellerType() { return sellerType; }
    public void setSellerType(String sellerType) { this.sellerType = sellerType; }
    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getRiskClassification() { return riskClassification; }
    public void setRiskClassification(String riskClassification) { this.riskClassification = riskClassification; }
    public String getEligibilityRuleJson() { return eligibilityRuleJson; }
    public void setEligibilityRuleJson(String eligibilityRuleJson) { this.eligibilityRuleJson = eligibilityRuleJson; }
    public String getClinicalRoute() { return clinicalRoute; }
    public void setClinicalRoute(String clinicalRoute) { this.clinicalRoute = clinicalRoute; }
    public String getChargeRef() { return chargeRef; }
    public void setChargeRef(String chargeRef) { this.chargeRef = chargeRef; }
    public String getPriceDisplay() { return priceDisplay; }
    public void setPriceDisplay(String priceDisplay) { this.priceDisplay = priceDisplay; }
    public String getFulfilmentMode() { return fulfilmentMode; }
    public void setFulfilmentMode(String fulfilmentMode) { this.fulfilmentMode = fulfilmentMode; }
    public String getFulfilmentOwner() { return fulfilmentOwner; }
    public void setFulfilmentOwner(String fulfilmentOwner) { this.fulfilmentOwner = fulfilmentOwner; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getModerationNotes() { return moderationNotes; }
    public void setModerationNotes(String moderationNotes) { this.moderationNotes = moderationNotes; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(OffsetDateTime approvedAt) { this.approvedAt = approvedAt; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }
    public String getFundoRef() { return fundoRef; }
    public void setFundoRef(String fundoRef) { this.fundoRef = fundoRef; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public int getLockVersion() { return lockVersion; }
    public void setLockVersion(int lockVersion) { this.lockVersion = lockVersion; }
}
