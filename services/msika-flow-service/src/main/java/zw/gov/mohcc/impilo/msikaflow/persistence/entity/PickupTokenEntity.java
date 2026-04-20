package zw.gov.mohcc.impilo.msikaflow.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import zw.gov.mohcc.impilo.msikaflow.domain.PickupTokenStatus;

/**
 * Represents a secure pickup token with hashed OTP for order collection.
 * The id is a ULID string assigned by the application layer.
 */
@Entity
@Table(name = "mf_pickup_tokens")
public class PickupTokenEntity {

    @Id
    @Column(name = "id", nullable = false, length = 26)
    private String id;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "otp_hash", nullable = false)
    private String otpHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PickupTokenStatus status;

    @Column(name = "claimed_by")
    private String claimedBy;

    @Column(name = "claimed_actor_type")
    private String claimedActorType;

    @Column(name = "claimed_at")
    private OffsetDateTime claimedAt;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "claim_meta", columnDefinition = "jsonb")
    private String claimMeta;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // Getters and setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getOtpHash() {
        return otpHash;
    }

    public void setOtpHash(String otpHash) {
        this.otpHash = otpHash;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public PickupTokenStatus getStatus() {
        return status;
    }

    public void setStatus(PickupTokenStatus status) {
        this.status = status;
    }

    public String getClaimedBy() {
        return claimedBy;
    }

    public void setClaimedBy(String claimedBy) {
        this.claimedBy = claimedBy;
    }

    public String getClaimedActorType() {
        return claimedActorType;
    }

    public void setClaimedActorType(String claimedActorType) {
        this.claimedActorType = claimedActorType;
    }

    public OffsetDateTime getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(OffsetDateTime claimedAt) {
        this.claimedAt = claimedAt;
    }

    public String getClaimMeta() {
        return claimMeta;
    }

    public void setClaimMeta(String claimMeta) {
        this.claimMeta = claimMeta;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
