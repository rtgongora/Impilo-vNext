package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Second-factor OTP challenge for the citizen "claim my record" flow (Identity
 * Journey Doctrine §2). The OTP is dispatched to the contact ALREADY RECORDED on
 * the resolved Health ID; only its Argon2id hash is stored here.
 */
@Entity
@Table(name = "record_claim_challenge", schema = "vito")
public class RecordClaimChallengeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "challenge_id", nullable = false, unique = true, updatable = false)
    private UUID challengeId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_health_id", nullable = false)
    private UUID clientHealthId;

    @Column(name = "otp_hash", nullable = false)
    private String otpHash;

    @Column(name = "contact_masked", length = 64)
    private String contactMasked;

    @Column(name = "channel", nullable = false, length = 16)
    private String channel = "phone";

    /** PENDING | VERIFIED | EXPIRED | LOCKED */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "PENDING";

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 5;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (challengeId == null) challengeId = UUID.randomUUID();
    }

    public Long getId() { return id; }
    public UUID getChallengeId() { return challengeId; }
    public void setChallengeId(UUID challengeId) { this.challengeId = challengeId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getClientHealthId() { return clientHealthId; }
    public void setClientHealthId(UUID clientHealthId) { this.clientHealthId = clientHealthId; }
    public String getOtpHash() { return otpHash; }
    public void setOtpHash(String otpHash) { this.otpHash = otpHash; }
    public String getContactMasked() { return contactMasked; }
    public void setContactMasked(String contactMasked) { this.contactMasked = contactMasked; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public OffsetDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(OffsetDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
