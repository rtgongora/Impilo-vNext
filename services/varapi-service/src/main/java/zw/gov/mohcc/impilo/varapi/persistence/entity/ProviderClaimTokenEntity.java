package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A single-use, expiring token authorising self-claim of a PRELOADED provider
 * profile (L3 W6 bootstrap chain).
 *
 * The token never authenticates — it only authorises the claim of one specific
 * preloaded {@link ProviderEntity} once the claimant's identity is established
 * by the normal person-first login. Only the SHA-256 hash of the token is
 * persisted, so a database read cannot replay a claim.
 */
@Entity
@Table(name = "provider_claim_token", schema = "varapi")
public class ProviderClaimTokenEntity {

    public static final String STATUS_ISSUED = "ISSUED";
    public static final String STATUS_CLAIMED = "CLAIMED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_REVOKED = "REVOKED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_ISSUED;

    @Column(name = "issued_to_hint", length = 255)
    private String issuedToHint;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "claimed_health_id")
    private UUID claimedHealthId;

    @Column(name = "preload_batch_id")
    private UUID preloadBatchId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /** True when the token can still be redeemed: issued and not past expiry. */
    public boolean isRedeemable(Instant now) {
        return STATUS_ISSUED.equals(status) && expiresAt != null && now.isBefore(expiresAt);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public Long getProviderId() { return providerId; }
    public void setProviderId(Long providerId) { this.providerId = providerId; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getIssuedToHint() { return issuedToHint; }
    public void setIssuedToHint(String issuedToHint) { this.issuedToHint = issuedToHint; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getClaimedAt() { return claimedAt; }
    public void setClaimedAt(Instant claimedAt) { this.claimedAt = claimedAt; }

    public UUID getClaimedHealthId() { return claimedHealthId; }
    public void setClaimedHealthId(UUID claimedHealthId) { this.claimedHealthId = claimedHealthId; }

    public UUID getPreloadBatchId() { return preloadBatchId; }
    public void setPreloadBatchId(UUID preloadBatchId) { this.preloadBatchId = preloadBatchId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
