package zw.gov.mohcc.impilo.shareslip.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Share link entity — represents a shareable document link with OTP protection.
 *
 * Uses a UUID[] column for document_ids, mapped via {@link UuidArrayConverter}.
 * Status lifecycle: ACTIVE -> CLAIMED | EXPIRED | REVOKED.
 */
@Entity
@Table(name = "share_links", schema = "share_slip")
public class ShareLinkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "link_id", nullable = false, unique = true, updatable = false)
    private UUID linkId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "share_token", nullable = false, unique = true, length = 64)
    private String shareToken;

    @Column(name = "subject_type", nullable = false, length = 50)
    private String subjectType;

    @Column(name = "subject_id", nullable = false)
    private String subjectId;

    @Column(name = "subject_name", length = 500)
    private String subjectName;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "purpose", length = 500)
    private String purpose;

    @Column(name = "document_ids", nullable = false, columnDefinition = "uuid[]")
    @Convert(converter = UuidArrayConverter.class)
    private List<UUID> documentIds;

    @Column(name = "verification_method", nullable = false, length = 30)
    private String verificationMethod = "OTP";

    @Column(name = "otp_hash")
    private String otpHash;

    @Column(name = "otp_attempts", nullable = false)
    private int otpAttempts = 0;

    @Column(name = "max_claims", nullable = false)
    private int maxClaims = 1;

    @Column(name = "claim_count", nullable = false)
    private int claimCount = 0;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "claimed_at")
    private OffsetDateTime claimedAt;

    @Column(name = "claimed_by")
    private String claimedBy;

    @Column(name = "claimed_device_fingerprint")
    private String claimedDeviceFingerprint;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (linkId == null) {
            linkId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getLinkId() { return linkId; }
    public void setLinkId(UUID linkId) { this.linkId = linkId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getShareToken() { return shareToken; }
    public void setShareToken(String shareToken) { this.shareToken = shareToken; }

    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }

    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public List<UUID> getDocumentIds() { return documentIds; }
    public void setDocumentIds(List<UUID> documentIds) { this.documentIds = documentIds; }

    public String getVerificationMethod() { return verificationMethod; }
    public void setVerificationMethod(String verificationMethod) { this.verificationMethod = verificationMethod; }

    public String getOtpHash() { return otpHash; }
    public void setOtpHash(String otpHash) { this.otpHash = otpHash; }

    public int getOtpAttempts() { return otpAttempts; }
    public void setOtpAttempts(int otpAttempts) { this.otpAttempts = otpAttempts; }

    public int getMaxClaims() { return maxClaims; }
    public void setMaxClaims(int maxClaims) { this.maxClaims = maxClaims; }

    public int getClaimCount() { return claimCount; }
    public void setClaimCount(int claimCount) { this.claimCount = claimCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }

    public OffsetDateTime getClaimedAt() { return claimedAt; }
    public void setClaimedAt(OffsetDateTime claimedAt) { this.claimedAt = claimedAt; }

    public String getClaimedBy() { return claimedBy; }
    public void setClaimedBy(String claimedBy) { this.claimedBy = claimedBy; }

    public String getClaimedDeviceFingerprint() { return claimedDeviceFingerprint; }
    public void setClaimedDeviceFingerprint(String claimedDeviceFingerprint) { this.claimedDeviceFingerprint = claimedDeviceFingerprint; }

    public OffsetDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(OffsetDateTime revokedAt) { this.revokedAt = revokedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
