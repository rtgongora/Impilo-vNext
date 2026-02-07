package zw.gov.mohcc.impilo.credential.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "signing_keys", schema = "credential_verification")
public class SigningKeyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_id", nullable = false, unique = true, length = 100)
    private String keyId;

    @Column(name = "algorithm", nullable = false, length = 50)
    private String algorithm;

    @Column(name = "public_key_hex", nullable = false, columnDefinition = "TEXT")
    private String publicKeyHex;

    @Column(name = "private_key_hex", nullable = false, columnDefinition = "TEXT")
    private String privateKeyHex;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = "ACTIVE";
        }
        if (algorithm == null) {
            algorithm = "Ed25519";
        }
        createdAt = Instant.now();
    }

    // Convenience methods

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }

    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

    public String getPublicKeyHex() { return publicKeyHex; }
    public void setPublicKeyHex(String publicKeyHex) { this.publicKeyHex = publicKeyHex; }

    public String getPrivateKeyHex() { return privateKeyHex; }
    public void setPrivateKeyHex(String privateKeyHex) { this.privateKeyHex = privateKeyHex; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getRotatedAt() { return rotatedAt; }
    public void setRotatedAt(Instant rotatedAt) { this.rotatedAt = rotatedAt; }
}
