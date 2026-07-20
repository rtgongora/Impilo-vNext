package zw.gov.mohcc.impilo.tshepo.keys.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * W4a: a named secret held in key custody, AES-256-GCM encrypted at rest with the
 * service KEK. Callers reference it by {@code secretRef} and never hold raw
 * material; resolve is INTERNAL-only + audited. Enables custody-backed dataset
 * tokenisation secrets (de-id) and integration credentials without env-inlined secrets.
 */
@Entity
@Table(name = "managed_secret", schema = "tshepo_keys")
public class ManagedSecretEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "secret_ref", nullable = false, length = 128)
    private String secretRef;

    @Column(name = "purpose", length = 64)
    private String purpose;

    /** [12-byte IV | ciphertext | GCM tag], AES-256-GCM under the service KEK. */
    @Column(name = "value_encrypted", nullable = false)
    private byte[] valueEncrypted;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "rotated_at")
    private OffsetDateTime rotatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getSecretRef() { return secretRef; }
    public void setSecretRef(String secretRef) { this.secretRef = secretRef; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public byte[] getValueEncrypted() { return valueEncrypted; }
    public void setValueEncrypted(byte[] valueEncrypted) { this.valueEncrypted = valueEncrypted; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getRotatedAt() { return rotatedAt; }
    public void setRotatedAt(OffsetDateTime rotatedAt) { this.rotatedAt = rotatedAt; }
}
