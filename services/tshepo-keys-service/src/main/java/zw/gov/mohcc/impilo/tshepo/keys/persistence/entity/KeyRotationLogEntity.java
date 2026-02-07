package zw.gov.mohcc.impilo.tshepo.keys.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for key rotation audit log entries.
 *
 * <p>Records every rotation event, capturing old and new key IDs,
 * who initiated the rotation, and the reason.</p>
 */
@Entity
@Table(name = "key_rotation_log", schema = "tshepo_keys")
public class KeyRotationLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "old_key_id", length = 64)
    private String oldKeyId;

    @Column(name = "new_key_id", nullable = false, length = 64)
    private String newKeyId;

    @Column(name = "rotated_by", nullable = false, length = 255)
    private String rotatedBy;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getOldKeyId() { return oldKeyId; }
    public void setOldKeyId(String oldKeyId) { this.oldKeyId = oldKeyId; }

    public String getNewKeyId() { return newKeyId; }
    public void setNewKeyId(String newKeyId) { this.newKeyId = newKeyId; }

    public String getRotatedBy() { return rotatedBy; }
    public void setRotatedBy(String rotatedBy) { this.rotatedBy = rotatedBy; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
