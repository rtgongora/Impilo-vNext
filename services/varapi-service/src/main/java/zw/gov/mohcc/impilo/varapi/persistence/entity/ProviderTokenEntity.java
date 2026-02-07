package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_tokens", schema = "varapi")
public class ProviderTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private ProviderEntity provider;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "lookup_hash", nullable = false, unique = true, length = 255)
    private String lookupHash;

    @Column(name = "argon2_verifier", length = 500)
    private String argon2Verifier;

    @Column(name = "status", length = 20)
    private String status = "ACTIVE";

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "issued_by", length = 255)
    private String issuedBy;

    @Column(name = "rotation_reason", columnDefinition = "TEXT")
    private String rotationReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_token_id")
    private ProviderTokenEntity previousToken;

    @PrePersist
    protected void onCreate() {
        issuedAt = Instant.now();
    }

    // Convenience methods

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean isRevoked() {
        return "REVOKED".equals(status);
    }

    public Instant getCreatedAt() {
        return issuedAt;
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ProviderEntity getProvider() { return provider; }
    public void setProvider(ProviderEntity provider) { this.provider = provider; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getLookupHash() { return lookupHash; }
    public void setLookupHash(String lookupHash) { this.lookupHash = lookupHash; }

    public String getArgon2Verifier() { return argon2Verifier; }
    public void setArgon2Verifier(String argon2Verifier) { this.argon2Verifier = argon2Verifier; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }

    public Instant getRotatedAt() { return rotatedAt; }
    public void setRotatedAt(Instant rotatedAt) { this.rotatedAt = rotatedAt; }

    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }

    public String getIssuedBy() { return issuedBy; }
    public void setIssuedBy(String issuedBy) { this.issuedBy = issuedBy; }

    public String getRotationReason() { return rotationReason; }
    public void setRotationReason(String rotationReason) { this.rotationReason = rotationReason; }

    public ProviderTokenEntity getPreviousToken() { return previousToken; }
    public void setPreviousToken(ProviderTokenEntity previousToken) { this.previousToken = previousToken; }
}
