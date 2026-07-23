package zw.gov.mohcc.impilo.msikaflow.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.msikaflow.domain.FundingMode;
import zw.gov.mohcc.impilo.msikaflow.domain.MarketplaceProfile;
import zw.gov.mohcc.impilo.msikaflow.domain.MarketplaceRequestStatus;
import zw.gov.mohcc.impilo.msikaflow.domain.PublicationMode;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A regulated request-for-offer published to eligible vendors (OF-B4, Vol II
 * §11.1 — spec logical name {@code mf_marketplace_requests}).
 *
 * <p>References the OROS order read-only via the ({@code orosOrderId},
 * {@code orosOrderVersionId}) pin — clinical truth is never copied here beyond
 * the §11.8 allow-list held in {@code publishedLinesJson}, which is the exact
 * PII-minimised snapshot broadcast to competing vendors (ZIBO/ATC-coded lines,
 * quantities, capability/urgency flags only — no name, no CPID/HID, no
 * diagnosis, no prescriber identity, no address).</p>
 */
@Entity
@Table(name = "mf_marketplace_requests")
public class MarketplaceRequestEntity {

    @Id
    @Column(name = "request_id", nullable = false, length = 26)
    private String requestId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "oros_order_id", nullable = false, length = 64)
    private String orosOrderId;

    /** The immutable version pin — commitment revalidates against this, never "latest". */
    @Column(name = "oros_order_version_id", nullable = false, length = 64)
    private String orosOrderVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile", nullable = false, length = 20)
    private MarketplaceProfile profile;

    @Enumerated(EnumType.STRING)
    @Column(name = "publication_mode", nullable = false, length = 32)
    private PublicationMode publicationMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private MarketplaceRequestStatus status;

    @Column(name = "controlled", nullable = false)
    private boolean controlled;

    /** Coarse ndila ward/district/catchment id only — never an address or coordinates. */
    @Column(name = "coarse_zone", length = 64)
    private String coarseZone;

    @Column(name = "urgency", length = 32)
    private String urgency;

    @Column(name = "offer_window_ends_at")
    private OffsetDateTime offerWindowEndsAt;

    @Column(name = "selection_window_ends_at")
    private OffsetDateTime selectionWindowEndsAt;

    /** The §11.8 PII-minimised snapshot actually broadcast — the only line truth held here. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "published_lines_json", nullable = false, columnDefinition = "jsonb")
    private String publishedLinesJson;

    /** Named vendor set for INVITED / PATIENT_REQUESTED_CHECK modes (JSON array of vendor ids). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "invited_vendor_ids", columnDefinition = "jsonb")
    private String invitedVendorIds;

    /**
     * Opaque prescription token for commitment step 6 (medication profiles).
     * NEVER included in any published snapshot, vendor view or event payload.
     */
    @Column(name = "prescription_token", length = 512)
    private String prescriptionToken;

    /**
     * OF-B8/OF-B9 — the funding-source election (§10.3). NULL is treated as
     * SELF_PAY everywhere (fail-closed: silence never fabricates coverage).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "funding_mode", length = 16)
    private FundingMode fundingMode;

    /**
     * Ruvimbo member-coverage reference for PAYER_COVERED flows. Held on the
     * request row only — NEVER included in the §11.8 published snapshot,
     * vendor views or event payloads.
     */
    @Column(name = "coverage_id")
    private UUID coverageId;

    @Column(name = "requested_by", length = 128)
    private String requestedBy;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

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

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getOrosOrderId() { return orosOrderId; }
    public void setOrosOrderId(String orosOrderId) { this.orosOrderId = orosOrderId; }

    public String getOrosOrderVersionId() { return orosOrderVersionId; }
    public void setOrosOrderVersionId(String orosOrderVersionId) { this.orosOrderVersionId = orosOrderVersionId; }

    public MarketplaceProfile getProfile() { return profile; }
    public void setProfile(MarketplaceProfile profile) { this.profile = profile; }

    public PublicationMode getPublicationMode() { return publicationMode; }
    public void setPublicationMode(PublicationMode publicationMode) { this.publicationMode = publicationMode; }

    public MarketplaceRequestStatus getStatus() { return status; }
    public void setStatus(MarketplaceRequestStatus status) { this.status = status; }

    public boolean isControlled() { return controlled; }
    public void setControlled(boolean controlled) { this.controlled = controlled; }

    public String getCoarseZone() { return coarseZone; }
    public void setCoarseZone(String coarseZone) { this.coarseZone = coarseZone; }

    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }

    public OffsetDateTime getOfferWindowEndsAt() { return offerWindowEndsAt; }
    public void setOfferWindowEndsAt(OffsetDateTime offerWindowEndsAt) { this.offerWindowEndsAt = offerWindowEndsAt; }

    public OffsetDateTime getSelectionWindowEndsAt() { return selectionWindowEndsAt; }
    public void setSelectionWindowEndsAt(OffsetDateTime selectionWindowEndsAt) { this.selectionWindowEndsAt = selectionWindowEndsAt; }

    public String getPublishedLinesJson() { return publishedLinesJson; }
    public void setPublishedLinesJson(String publishedLinesJson) { this.publishedLinesJson = publishedLinesJson; }

    public String getInvitedVendorIds() { return invitedVendorIds; }
    public void setInvitedVendorIds(String invitedVendorIds) { this.invitedVendorIds = invitedVendorIds; }

    public String getPrescriptionToken() { return prescriptionToken; }
    public void setPrescriptionToken(String prescriptionToken) { this.prescriptionToken = prescriptionToken; }

    public FundingMode getFundingMode() { return fundingMode; }
    public void setFundingMode(FundingMode fundingMode) { this.fundingMode = fundingMode; }

    public UUID getCoverageId() { return coverageId; }
    public void setCoverageId(UUID coverageId) { this.coverageId = coverageId; }

    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
