package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "patient_share_artifact", schema = "vito")
public class PatientShareArtifactEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "share_request_id", nullable = false)
    private Long shareRequestId;

    @Column(name = "artifact_type", nullable = false, length = 32)
    private String artifactType = "LINK";

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "otp_hash", nullable = false)
    private String otpHash;

    @Column(name = "active_flag", nullable = false)
    private boolean activeFlag = true;

    @Column(name = "one_time_use_flag", nullable = false)
    private boolean oneTimeUseFlag = true;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "otp_attempts", nullable = false)
    private int otpAttempts = 0;

    @Column(name = "max_otp_attempts", nullable = false)
    private int maxOtpAttempts = 5;

    @Column(name = "use_count", nullable = false)
    private int useCount = 0;

    @PrePersist
    void onCreate() {
        issuedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getShareRequestId() {
        return shareRequestId;
    }

    public void setShareRequestId(Long shareRequestId) {
        this.shareRequestId = shareRequestId;
    }

    public String getArtifactType() {
        return artifactType;
    }

    public void setArtifactType(String artifactType) {
        this.artifactType = artifactType;
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

    public boolean isActiveFlag() {
        return activeFlag;
    }

    public void setActiveFlag(boolean activeFlag) {
        this.activeFlag = activeFlag;
    }

    public boolean isOneTimeUseFlag() {
        return oneTimeUseFlag;
    }

    public void setOneTimeUseFlag(boolean oneTimeUseFlag) {
        this.oneTimeUseFlag = oneTimeUseFlag;
    }

    public OffsetDateTime getIssuedAt() {
        return issuedAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(OffsetDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public int getOtpAttempts() {
        return otpAttempts;
    }

    public void setOtpAttempts(int otpAttempts) {
        this.otpAttempts = otpAttempts;
    }

    public int getMaxOtpAttempts() {
        return maxOtpAttempts;
    }

    public void setMaxOtpAttempts(int maxOtpAttempts) {
        this.maxOtpAttempts = maxOtpAttempts;
    }

    public int getUseCount() {
        return useCount;
    }

    public void setUseCount(int useCount) {
        this.useCount = useCount;
    }
}
