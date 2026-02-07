package zw.gov.mohcc.impilo.pharmacy.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import zw.gov.mohcc.impilo.pharmacy.domain.PickupMethod;
import zw.gov.mohcc.impilo.pharmacy.domain.PickupStatus;

/**
 * Records proof of medication pickup, supporting OTP, biometric,
 * ID-check, delegated, and waiver verification methods.
 */
@Entity
@Table(name = "rx_pickup_proofs")
public class PickupProofEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "proof_id", nullable = false)
    private UUID proofId;

    @Column(name = "dispense_order_id", nullable = false)
    private UUID dispenseOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    private PickupMethod method;

    @Column(name = "token_hash")
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PickupStatus status = PickupStatus.PENDING;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "delegated_to")
    private String delegatedTo;

    @Column(name = "claimed_by")
    private String claimedBy;

    @Column(name = "claimed_at")
    private OffsetDateTime claimedAt;

    @Column(name = "device_fingerprint")
    private String deviceFingerprint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // Getters and setters

    public UUID getProofId() { return proofId; }
    public void setProofId(UUID proofId) { this.proofId = proofId; }

    public UUID getDispenseOrderId() { return dispenseOrderId; }
    public void setDispenseOrderId(UUID dispenseOrderId) { this.dispenseOrderId = dispenseOrderId; }

    public PickupMethod getMethod() { return method; }
    public void setMethod(PickupMethod method) { this.method = method; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public PickupStatus getStatus() { return status; }
    public void setStatus(PickupStatus status) { this.status = status; }

    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }

    public String getDelegatedTo() { return delegatedTo; }
    public void setDelegatedTo(String delegatedTo) { this.delegatedTo = delegatedTo; }

    public String getClaimedBy() { return claimedBy; }
    public void setClaimedBy(String claimedBy) { this.claimedBy = claimedBy; }

    public OffsetDateTime getClaimedAt() { return claimedAt; }
    public void setClaimedAt(OffsetDateTime claimedAt) { this.claimedAt = claimedAt; }

    public String getDeviceFingerprint() { return deviceFingerprint; }
    public void setDeviceFingerprint(String deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
