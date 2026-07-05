package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Evidence trail for platform provider trust transitions (IATG Wave 1).
 *
 * <p>Shape cloned from {@link ExternalProviderVerificationAttemptEntity}, but anchored
 * on the canonical {@link ProviderEntity} rather than the external collaboration
 * profile. EVERY {@code ProviderTrustService} transition — upgrade, reaffirmation, or
 * evidence-revoked downgrade — writes exactly one row here.</p>
 *
 * <p>{@code evidenceRef} is matching evidence, never identity: it may reference a
 * council record, document id, or employment record, and is therefore MASKED in all
 * API responses (council numbers must never appear in payloads).</p>
 */
@Entity
@Table(name = "provider_verification_attempt", schema = "varapi")
public class ProviderVerificationAttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id")
    private ProviderEntity provider;

    @Column(name = "evidence_type", nullable = false, length = 64)
    private String evidenceType;

    /** COUNCIL_RECORD | HSC_EMPLOYMENT | DOCUMENT | ORG_ATTESTATION */
    @Column(name = "evidence_source", nullable = false, length = 64)
    private String evidenceSource;

    @Column(name = "outcome", nullable = false, length = 40)
    private String outcome;

    @Column(name = "confidence", precision = 5, scale = 2)
    private BigDecimal confidence;

    @Column(name = "evidence_ref", length = 255)
    private String evidenceRef;

    @Column(name = "attempted_by", length = 255)
    private String attemptedBy;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    @PrePersist
    void pc() {
        if (attemptedAt == null) {
            attemptedAt = Instant.now();
        }
    }

    public Long getId() { return id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public ProviderEntity getProvider() { return provider; }
    public void setProvider(ProviderEntity provider) { this.provider = provider; }

    public String getEvidenceType() { return evidenceType; }
    public void setEvidenceType(String evidenceType) { this.evidenceType = evidenceType; }

    public String getEvidenceSource() { return evidenceSource; }
    public void setEvidenceSource(String evidenceSource) { this.evidenceSource = evidenceSource; }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }

    public String getEvidenceRef() { return evidenceRef; }
    public void setEvidenceRef(String evidenceRef) { this.evidenceRef = evidenceRef; }

    public String getAttemptedBy() { return attemptedBy; }
    public void setAttemptedBy(String attemptedBy) { this.attemptedBy = attemptedBy; }

    public Instant getAttemptedAt() { return attemptedAt; }
    public void setAttemptedAt(Instant attemptedAt) { this.attemptedAt = attemptedAt; }
}
