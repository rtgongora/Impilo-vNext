package zw.gov.mohcc.impilo.pharmacy.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import zw.gov.mohcc.impilo.pharmacy.domain.PickupMethod;
import zw.gov.mohcc.impilo.pharmacy.domain.PickupStatus;
import zw.gov.mohcc.impilo.pharmacy.domain.PickupVerificationGrade;

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

    /**
     * M3 BUG-5 — the freshly generated plaintext credential (OTP / QR token /
     * SMS reference), carried in memory from {@code createProof} to the
     * creation response ONLY. {@code @Transient}: NEVER persisted (the old
     * deviceFingerprint stash was a managed column, so dirty-checking flushed
     * the plaintext to the database at commit); only the HMAC hash is stored.
     */
    @Transient
    private String oneTimeCredential;

    // ── OF-B16 collector identity + verification grade (§8.11.3, §12.4) ──
    /** The identity of the person who physically collected (may differ from actor). */
    @Column(name = "collector_identity")
    private String collectorIdentity;

    /** Proof-ladder grade the collection was verified at (server-assigned). */
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_grade")
    private PickupVerificationGrade verificationGrade;

    /** Named-recipient path: collection restricted to this named person. */
    @Column(name = "named_recipient")
    private String namedRecipient;

    /** HMAC hash of the opaque SMS reference (no token ever rides an SMS). */
    @Column(name = "sms_reference_hash")
    private String smsReferenceHash;

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

    public String getOneTimeCredential() { return oneTimeCredential; }
    public void setOneTimeCredential(String oneTimeCredential) { this.oneTimeCredential = oneTimeCredential; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public String getCollectorIdentity() { return collectorIdentity; }
    public void setCollectorIdentity(String collectorIdentity) { this.collectorIdentity = collectorIdentity; }

    public PickupVerificationGrade getVerificationGrade() { return verificationGrade; }
    public void setVerificationGrade(PickupVerificationGrade verificationGrade) { this.verificationGrade = verificationGrade; }

    public String getNamedRecipient() { return namedRecipient; }
    public void setNamedRecipient(String namedRecipient) { this.namedRecipient = namedRecipient; }

    public String getSmsReferenceHash() { return smsReferenceHash; }
    public void setSmsReferenceHash(String smsReferenceHash) { this.smsReferenceHash = smsReferenceHash; }
}
