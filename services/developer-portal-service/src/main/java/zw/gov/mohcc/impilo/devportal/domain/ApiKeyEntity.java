package zw.gov.mohcc.impilo.devportal.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dvp_api_keys")
public class ApiKeyEntity {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    @Column(name = "client_id", nullable = false) private UUID clientId;
    @Column(name = "key_prefix", nullable = false) private String keyPrefix;
    @Column(name = "key_hash", nullable = false) private String keyHash;
    @Column(name = "label") private String label;
    @Column(name = "status", nullable = false) private String status = "ACTIVE";
    @Column(name = "last_used_at") private OffsetDateTime lastUsedAt;
    @Column(name = "expires_at") private OffsetDateTime expiresAt;
    @Column(name = "rotated_from_id") private UUID rotatedFromId;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getClientId() { return clientId; }
    public void setClientId(UUID v) { this.clientId = v; }
    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String v) { this.keyPrefix = v; }
    public String getKeyHash() { return keyHash; }
    public void setKeyHash(String v) { this.keyHash = v; }
    public String getLabel() { return label; }
    public void setLabel(String v) { this.label = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public OffsetDateTime getLastUsedAt() { return lastUsedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime v) { this.expiresAt = v; }
    public UUID getRotatedFromId() { return rotatedFromId; }
    public void setRotatedFromId(UUID v) { this.rotatedFromId = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
