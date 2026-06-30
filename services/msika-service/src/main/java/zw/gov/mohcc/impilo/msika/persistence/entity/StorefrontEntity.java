package zw.gov.mohcc.impilo.msika.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A seller's branded presence in the Msika marketplace. Seller identity
 * (provider/facility/site/programme/vendor) is validated upstream against the
 * owning registries (Varapi/Tuso/Indawo); this row records only the validation
 * outcome and the buyer-facing presentation. No PII, no payment, no stock.
 */
@Entity
@Table(name = "msika_storefronts")
public class StorefrontEntity {

    @Id
    @Column(name = "storefront_id", length = 26)
    private String storefrontId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "seller_type", nullable = false, length = 20)
    private String sellerType;

    @Column(name = "seller_id", nullable = false, length = 128)
    private String sellerId;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "description")
    private String description;

    @Column(name = "verification_status", nullable = false, length = 20)
    private String verificationStatus = "UNVERIFIED";

    @Column(name = "verified_by", length = 128)
    private String verifiedBy;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contact_refs", nullable = false, columnDefinition = "jsonb")
    private String contactRefsJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "service_area", nullable = false, columnDefinition = "jsonb")
    private String serviceAreaJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (contactRefsJson == null) contactRefsJson = "{}";
        if (serviceAreaJson == null) serviceAreaJson = "{}";
        if (metadataJson == null) metadataJson = "{}";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public String getStorefrontId() { return storefrontId; }
    public void setStorefrontId(String storefrontId) { this.storefrontId = storefrontId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getSellerType() { return sellerType; }
    public void setSellerType(String sellerType) { this.sellerType = sellerType; }
    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }
    public String getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(String verifiedBy) { this.verifiedBy = verifiedBy; }
    public OffsetDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(OffsetDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
    public String getContactRefsJson() { return contactRefsJson; }
    public void setContactRefsJson(String contactRefsJson) { this.contactRefsJson = contactRefsJson; }
    public String getServiceAreaJson() { return serviceAreaJson; }
    public void setServiceAreaJson(String serviceAreaJson) { this.serviceAreaJson = serviceAreaJson; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
