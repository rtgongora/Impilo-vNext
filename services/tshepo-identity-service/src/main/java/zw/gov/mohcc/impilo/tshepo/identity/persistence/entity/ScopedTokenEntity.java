package zw.gov.mohcc.impilo.tshepo.identity.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Persisted record of a scoped access token for revocation and introspection.
 *
 * <p>Scoped tokens are short-lived JWS (Ed25519-signed) tokens issued to actors
 * for specific downstream service calls. This table supports:</p>
 * <ul>
 *   <li>Revocation: set revoked_at and status to REVOKED</li>
 *   <li>Introspection: check status + expiry without parsing the JWS</li>
 *   <li>Audit: who was issued what token, when, and for what purpose</li>
 * </ul>
 */
@Entity
@Table(name = "scoped_token", schema = "tshepo_identity")
public class ScopedTokenEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "actor_id", nullable = false, length = 255)
    private String actorId;

    @Column(name = "target_service", nullable = false, length = 128)
    private String targetService;

    @Column(name = "scope", nullable = false, length = 255)
    private String scope;

    @Column(name = "subject_ref", length = 255)
    private String subjectRef;

    @Column(name = "jti", nullable = false, unique = true, length = 64)
    private String jti;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    /** SERVICE (S2S scoped token) | WORK_CONTEXT (duty-scoped work token, D-P3). */
    @Column(name = "token_kind", nullable = false, length = 32)
    private String tokenKind = "SERVICE";

    /**
     * WORK_CONTEXT only: the stable-for-the-session context claims
     * (facility/department/workspace/role template/provider public id/purpose/
     * session assurance) as JSON. Live licence/scope truth stays PDP-resolved.
     */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "context_claims", columnDefinition = "jsonb")
    private String contextClaims;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        this.issuedAt = Instant.now();
    }

    // --- Getters and Setters ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getTargetService() { return targetService; }
    public void setTargetService(String targetService) { this.targetService = targetService; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getSubjectRef() { return subjectRef; }
    public void setSubjectRef(String subjectRef) { this.subjectRef = subjectRef; }

    public String getJti() { return jti; }
    public void setJti(String jti) { this.jti = jti; }

    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTokenKind() { return tokenKind; }
    public void setTokenKind(String tokenKind) { this.tokenKind = tokenKind; }

    public String getContextClaims() { return contextClaims; }
    public void setContextClaims(String contextClaims) { this.contextClaims = contextClaims; }
}
