package zw.gov.mohcc.impilo.tshepo.keys.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for Ed25519 signing key pairs.
 *
 * <p>Private keys are stored encrypted (AES-256-GCM) in {@code privateKeyEncrypted}.
 * The {@code publicKeyPem} is PEM-encoded SubjectPublicKeyInfo for distribution.</p>
 */
@Entity
@Table(name = "signing_key", schema = "tshepo_keys")
public class SigningKeyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "key_id", nullable = false, unique = true, length = 64)
    private String keyId;

    @Column(name = "algorithm", nullable = false, length = 32)
    private String algorithm;

    @Column(name = "public_key_pem", nullable = false, columnDefinition = "TEXT")
    private String publicKeyPem;

    @Column(name = "private_key_encrypted", nullable = false)
    private byte[] privateKeyEncrypted;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    /** Canonical key purpose (see {@link zw.gov.mohcc.impilo.tshepo.keys.core.KeyPurpose}). */
    @Column(name = "purpose", nullable = false, length = 32)
    private String purpose = "GENERAL";

    /** Human-friendly key alias for discovery/operations. */
    @Column(name = "alias", length = 128)
    private String alias;

    /** Issuing trust authority / origin of the key (custody origin). */
    @Column(name = "issuer", length = 128)
    private String issuer;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (purpose == null || purpose.isBlank()) {
            purpose = "GENERAL";
        }
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }

    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

    public String getPublicKeyPem() { return publicKeyPem; }
    public void setPublicKeyPem(String publicKeyPem) { this.publicKeyPem = publicKeyPem; }

    public byte[] getPrivateKeyEncrypted() { return privateKeyEncrypted; }
    public void setPrivateKeyEncrypted(byte[] privateKeyEncrypted) { this.privateKeyEncrypted = privateKeyEncrypted; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getRotatedAt() { return rotatedAt; }
    public void setRotatedAt(Instant rotatedAt) { this.rotatedAt = rotatedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
