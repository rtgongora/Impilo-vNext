package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JWS/Verifiable Credential snapshot packed for offline card use.
 * Maps to vito.health_summary_credential (V010).
 */
@Entity
@Table(name = "health_summary_credential", schema = "vito")
public class HealthSummaryCredentialEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "health_id", nullable = false)
    private UUID healthId;

    @Column(name = "card_id")
    private Long cardId;

    @Column(name = "credential_type", nullable = false)
    private String credentialType;

    @Column(name = "fhir_bundle_ref")
    private String fhirBundleRef;

    @Column(name = "jws_compact", nullable = false, columnDefinition = "text")
    private String jwsCompact;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        if (issuedAt == null) issuedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getHealthId() { return healthId; }
    public void setHealthId(UUID healthId) { this.healthId = healthId; }
    public Long getCardId() { return cardId; }
    public void setCardId(Long cardId) { this.cardId = cardId; }
    public String getCredentialType() { return credentialType; }
    public void setCredentialType(String credentialType) { this.credentialType = credentialType; }
    public String getFhirBundleRef() { return fhirBundleRef; }
    public void setFhirBundleRef(String fhirBundleRef) { this.fhirBundleRef = fhirBundleRef; }
    public String getJwsCompact() { return jwsCompact; }
    public void setJwsCompact(String jwsCompact) { this.jwsCompact = jwsCompact; }
    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(OffsetDateTime issuedAt) { this.issuedAt = issuedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(OffsetDateTime revokedAt) { this.revokedAt = revokedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
