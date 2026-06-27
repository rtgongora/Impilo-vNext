package zw.gov.mohcc.impilo.tshepo.authz.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Step-up authentication challenge.
 *
 * <p>Issued when the PolicyEngine determines that a higher level of
 * assurance is required (risk-based step-up or break-glass prerequisite).
 * Challenges have a configurable TTL (default 300 seconds) after which
 * they expire automatically.</p>
 *
 * <p>Status transitions: PENDING -> COMPLETED | EXPIRED | FAILED</p>
 */
@Entity
@Table(name = "step_up_challenge", schema = "tshepo_authz")
public class StepUpChallengeEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Column(name = "challenge_type", nullable = false, length = 32)
    private String challengeType;

    /** Verification mode (TOTP / SMS_OTP / BIOMETRIC / SUPERVISOR_APPROVAL). */
    @Column(name = "mode", length = 32)
    private String mode;

    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    /** Hashed verification secret (e.g. SHA-256 of an issued SMS OTP); never the raw code. */
    @Column(name = "verification_secret_hash", length = 128)
    private String verificationSecretHash;

    @Column(name = "purpose", length = 64)
    private String purpose;

    @Column(name = "requested_action", length = 255)
    private String requestedAction;

    @Column(name = "risk_level", length = 16)
    private String riskLevel;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    /** Authorised supervisor principal that approved a SUPERVISOR_APPROVAL challenge. */
    @Column(name = "approved_by", length = 255)
    private String approvedBy;

    @Column(name = "audit_metadata", columnDefinition = "TEXT")
    private String auditMetadata;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.issuedAt == null) {
            this.issuedAt = Instant.now();
        }
    }

    // ── Getters and Setters ────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getChallengeType() { return challengeType; }
    public void setChallengeType(String challengeType) { this.challengeType = challengeType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getVerificationSecretHash() { return verificationSecretHash; }
    public void setVerificationSecretHash(String verificationSecretHash) { this.verificationSecretHash = verificationSecretHash; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public String getRequestedAction() { return requestedAction; }
    public void setRequestedAction(String requestedAction) { this.requestedAction = requestedAction; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public String getAuditMetadata() { return auditMetadata; }
    public void setAuditMetadata(String auditMetadata) { this.auditMetadata = auditMetadata; }

    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
