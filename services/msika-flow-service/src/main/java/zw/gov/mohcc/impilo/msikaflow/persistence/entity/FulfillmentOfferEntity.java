package zw.gov.mohcc.impilo.msikaflow.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.msikaflow.domain.OfferStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A vendor's structured, TTL-bounded offer against a marketplace request
 * (OF-B6, Vol II §9.4/§11.4 — spec logical name {@code mf_fulfilment_offers};
 * table uses the repo's US-spelling convention).
 */
@Entity
@Table(name = "mf_fulfillment_offers")
public class FulfillmentOfferEntity {

    @Id
    @Column(name = "offer_id", nullable = false, length = 26)
    private String offerId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "request_id", nullable = false, length = 26)
    private String requestId;

    @Column(name = "invitation_id", nullable = false, length = 26)
    private String invitationId;

    @Column(name = "vendor_id", nullable = false, length = 26)
    private String vendorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private OfferStatus status;

    /** Coded reason for terminal states (RC-2/3/6/7 codes, REQUEST_CANCELLED, …). */
    @Column(name = "status_reason", length = 64)
    private String statusReason;

    @Column(name = "price_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal priceTotal;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency;

    @Column(name = "fulfillment_window_hours")
    private Integer fulfillmentWindowHours;

    /**
     * Availability-promise expiry. While ACTIVE this is the vendor's TTL; from
     * SELECTED onward it is re-bound to the DURA reservation TTL (§9.4 binding
     * TTL semantics — fail-close, they expire together).
     */
    @Column(name = "ttl_expires_at")
    private OffsetDateTime ttlExpiresAt;

    /**
     * OF-B13 — the vendor offers home specimen collection (DIAGNOSTICS profile
     * only; §8.5 offer-variant "scheduled-service"). When true a concrete
     * collection window is mandatory.
     */
    @Column(name = "home_collection", nullable = false)
    private boolean homeCollection;

    @Column(name = "collection_window_start")
    private OffsetDateTime collectionWindowStart;

    @Column(name = "collection_window_end")
    private OffsetDateTime collectionWindowEnd;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "eligibility_snapshot_json", columnDefinition = "jsonb")
    private String eligibilitySnapshotJson;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

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

    public String getOfferId() { return offerId; }
    public void setOfferId(String offerId) { this.offerId = offerId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getInvitationId() { return invitationId; }
    public void setInvitationId(String invitationId) { this.invitationId = invitationId; }

    public String getVendorId() { return vendorId; }
    public void setVendorId(String vendorId) { this.vendorId = vendorId; }

    public OfferStatus getStatus() { return status; }
    public void setStatus(OfferStatus status) { this.status = status; }

    public String getStatusReason() { return statusReason; }
    public void setStatusReason(String statusReason) { this.statusReason = statusReason; }

    public BigDecimal getPriceTotal() { return priceTotal; }
    public void setPriceTotal(BigDecimal priceTotal) { this.priceTotal = priceTotal; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Integer getFulfillmentWindowHours() { return fulfillmentWindowHours; }
    public void setFulfillmentWindowHours(Integer fulfillmentWindowHours) { this.fulfillmentWindowHours = fulfillmentWindowHours; }

    public OffsetDateTime getTtlExpiresAt() { return ttlExpiresAt; }
    public void setTtlExpiresAt(OffsetDateTime ttlExpiresAt) { this.ttlExpiresAt = ttlExpiresAt; }

    public boolean isHomeCollection() { return homeCollection; }
    public void setHomeCollection(boolean homeCollection) { this.homeCollection = homeCollection; }

    public OffsetDateTime getCollectionWindowStart() { return collectionWindowStart; }
    public void setCollectionWindowStart(OffsetDateTime collectionWindowStart) { this.collectionWindowStart = collectionWindowStart; }

    public OffsetDateTime getCollectionWindowEnd() { return collectionWindowEnd; }
    public void setCollectionWindowEnd(OffsetDateTime collectionWindowEnd) { this.collectionWindowEnd = collectionWindowEnd; }

    public String getEligibilitySnapshotJson() { return eligibilitySnapshotJson; }
    public void setEligibilitySnapshotJson(String eligibilitySnapshotJson) { this.eligibilitySnapshotJson = eligibilitySnapshotJson; }

    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
