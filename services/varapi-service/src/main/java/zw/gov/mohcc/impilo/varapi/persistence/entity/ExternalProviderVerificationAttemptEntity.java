package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "external_provider_verification_attempt", schema = "varapi")
public class ExternalProviderVerificationAttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "external_profile_id")
    private ExternalProviderAccessProfileEntity profile;

    @Column(name = "verification_type", nullable = false, length = 64)
    private String verificationType;

    @Column(name = "source_type", nullable = false, length = 64)
    private String sourceType;

    @Column(name = "outcome", nullable = false, length = 32)
    private String outcome;

    @Column(name = "confidence_score", precision = 5, scale = 2)
    private BigDecimal confidenceScore;

    @Column(name = "matched_provider_public_id", length = 26)
    private String matchedProviderPublicId;

    @Column(name = "notes")
    private String notes;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @PrePersist
    void pc() {
        Instant n = Instant.now();
        if (requestedAt == null) {
            requestedAt = n;
        }
        if (completedAt == null) {
            completedAt = n;
        }
    }

    public Long getId() {
        return id;
    }

    public ExternalProviderAccessProfileEntity getProfile() {
        return profile;
    }

    public void setProfile(ExternalProviderAccessProfileEntity profile) {
        this.profile = profile;
    }

    public String getVerificationType() {
        return verificationType;
    }

    public void setVerificationType(String verificationType) {
        this.verificationType = verificationType;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public BigDecimal getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(BigDecimal confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getMatchedProviderPublicId() {
        return matchedProviderPublicId;
    }

    public void setMatchedProviderPublicId(String matchedProviderPublicId) {
        this.matchedProviderPublicId = matchedProviderPublicId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
