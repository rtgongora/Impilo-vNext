package zw.gov.mohcc.impilo.tshepo.authz.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

/**
 * Device reputation profile for risk scoring.
 *
 * <p>Tracks device fingerprints across sessions. New devices start with
 * a configurable risk score (default 50). Known devices accumulate trust
 * over time. Blocked devices score 100 and are denied outright.</p>
 */
@Entity
@Table(name = "device_profile", schema = "tshepo_authz",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "fingerprint"}))
public class DeviceProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String fingerprint;

    @Column(name = "actor_id")
    private String actorId;

    @Column(name = "risk_score", nullable = false)
    private int riskScore = 0;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(nullable = false)
    private boolean blocked = false;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    // ── Getters and Setters ────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public int getRiskScore() { return riskScore; }
    public void setRiskScore(int riskScore) { this.riskScore = riskScore; }

    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }

    public boolean isBlocked() { return blocked; }
    public void setBlocked(boolean blocked) { this.blocked = blocked; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
