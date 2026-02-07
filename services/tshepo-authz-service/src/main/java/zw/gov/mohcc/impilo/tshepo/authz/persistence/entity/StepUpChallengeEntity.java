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

    @Column(nullable = false, length = 16)
    private String status = "PENDING";

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

    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
