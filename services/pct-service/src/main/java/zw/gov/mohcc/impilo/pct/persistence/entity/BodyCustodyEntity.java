package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Body / mortuary custody record for a death case (WS#8). This is the minimal real
 * mortuary module owned inside pct-service — not a new sovereign service. Tracks body
 * receipt, tagging, storage, legal / post-mortem holds, and release authorisation.
 * Body release is BLOCKED while a legal or post-mortem hold is active, and may only be
 * recorded to a named, authorised recipient.
 */
@Entity
@Table(name = "pct_body_custody")
public class BodyCustodyEntity {

    @Id
    @Column(name = "custody_id", nullable = false)
    private UUID custodyId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "body_tag", nullable = false)
    private String bodyTag;

    @Column(name = "storage_location")
    private String storageLocation;

    @Column(name = "status", nullable = false)
    private String status = "RECEIVED";

    @Column(name = "received_by")
    private String receivedBy;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(name = "legal_hold", nullable = false)
    private boolean legalHold = false;

    @Column(name = "legal_hold_reason")
    private String legalHoldReason;

    @Column(name = "postmortem_hold", nullable = false)
    private boolean postmortemHold = false;

    @Column(name = "release_authorised_by")
    private String releaseAuthorisedBy;

    @Column(name = "released_to_name")
    private String releasedToName;

    @Column(name = "released_to_relationship")
    private String releasedToRelationship;

    @Column(name = "released_to_id_ref")
    private String releasedToIdRef;

    @Column(name = "released_at")
    private OffsetDateTime releasedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getCustodyId() { return custodyId; }
    public void setCustodyId(UUID custodyId) { this.custodyId = custodyId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID caseId) { this.caseId = caseId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public String getBodyTag() { return bodyTag; }
    public void setBodyTag(String bodyTag) { this.bodyTag = bodyTag; }
    public String getStorageLocation() { return storageLocation; }
    public void setStorageLocation(String storageLocation) { this.storageLocation = storageLocation; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReceivedBy() { return receivedBy; }
    public void setReceivedBy(String receivedBy) { this.receivedBy = receivedBy; }
    public OffsetDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(OffsetDateTime receivedAt) { this.receivedAt = receivedAt; }
    public boolean isLegalHold() { return legalHold; }
    public void setLegalHold(boolean legalHold) { this.legalHold = legalHold; }
    public String getLegalHoldReason() { return legalHoldReason; }
    public void setLegalHoldReason(String legalHoldReason) { this.legalHoldReason = legalHoldReason; }
    public boolean isPostmortemHold() { return postmortemHold; }
    public void setPostmortemHold(boolean postmortemHold) { this.postmortemHold = postmortemHold; }
    public String getReleaseAuthorisedBy() { return releaseAuthorisedBy; }
    public void setReleaseAuthorisedBy(String releaseAuthorisedBy) { this.releaseAuthorisedBy = releaseAuthorisedBy; }
    public String getReleasedToName() { return releasedToName; }
    public void setReleasedToName(String releasedToName) { this.releasedToName = releasedToName; }
    public String getReleasedToRelationship() { return releasedToRelationship; }
    public void setReleasedToRelationship(String releasedToRelationship) { this.releasedToRelationship = releasedToRelationship; }
    public String getReleasedToIdRef() { return releasedToIdRef; }
    public void setReleasedToIdRef(String releasedToIdRef) { this.releasedToIdRef = releasedToIdRef; }
    public OffsetDateTime getReleasedAt() { return releasedAt; }
    public void setReleasedAt(OffsetDateTime releasedAt) { this.releasedAt = releasedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
