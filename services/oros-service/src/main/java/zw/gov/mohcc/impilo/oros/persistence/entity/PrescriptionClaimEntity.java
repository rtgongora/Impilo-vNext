package zw.gov.mohcc.impilo.oros.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A prescription claim (OF-B2, Vol II §13.2.3): the atomic act that decremented
 * the server-side repeats counter and authorised exactly one dispense episode.
 * Release (compensation) restores the counter with an audited event (§9.2).
 */
@Entity
@Table(name = "oros_prescription_claims")
public class PrescriptionClaimEntity {

    public static final String STATUS_CLAIMED = "CLAIMED";
    public static final String STATUS_RELEASED = "RELEASED";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "claim_id")
    private UUID claimId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "token_id", nullable = false, length = 26)
    private String tokenId;

    @Column(name = "prescription_id", nullable = false, length = 26)
    private String prescriptionId;

    @Column(name = "prescription_version_id", nullable = false, length = 26)
    private String prescriptionVersionId;

    @Column(name = "claimed_by_actor", length = 128)
    private String claimedByActor;

    @Column(name = "claimed_by_facility")
    private UUID claimedByFacility;

    @Column(name = "vendor_ref", length = 128)
    private String vendorRef;

    @Column(name = "dispense_episode_ref", length = 128)
    private String dispenseEpisodeRef;

    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_CLAIMED;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "claimed_at", nullable = false)
    private OffsetDateTime claimedAt = OffsetDateTime.now();

    @Column(name = "released_at")
    private OffsetDateTime releasedAt;

    @Column(name = "release_reason", length = 512)
    private String releaseReason;

    public UUID getClaimId() { return claimId; }
    public void setClaimId(UUID claimId) { this.claimId = claimId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getTokenId() { return tokenId; }
    public void setTokenId(String tokenId) { this.tokenId = tokenId; }
    public String getPrescriptionId() { return prescriptionId; }
    public void setPrescriptionId(String prescriptionId) { this.prescriptionId = prescriptionId; }
    public String getPrescriptionVersionId() { return prescriptionVersionId; }
    public void setPrescriptionVersionId(String v) { this.prescriptionVersionId = v; }
    public String getClaimedByActor() { return claimedByActor; }
    public void setClaimedByActor(String claimedByActor) { this.claimedByActor = claimedByActor; }
    public UUID getClaimedByFacility() { return claimedByFacility; }
    public void setClaimedByFacility(UUID claimedByFacility) { this.claimedByFacility = claimedByFacility; }
    public String getVendorRef() { return vendorRef; }
    public void setVendorRef(String vendorRef) { this.vendorRef = vendorRef; }
    public String getDispenseEpisodeRef() { return dispenseEpisodeRef; }
    public void setDispenseEpisodeRef(String dispenseEpisodeRef) { this.dispenseEpisodeRef = dispenseEpisodeRef; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public OffsetDateTime getClaimedAt() { return claimedAt; }
    public void setClaimedAt(OffsetDateTime claimedAt) { this.claimedAt = claimedAt; }
    public OffsetDateTime getReleasedAt() { return releasedAt; }
    public void setReleasedAt(OffsetDateTime releasedAt) { this.releasedAt = releasedAt; }
    public String getReleaseReason() { return releaseReason; }
    public void setReleaseReason(String releaseReason) { this.releaseReason = releaseReason; }
}
