package zw.gov.mohcc.impilo.ia.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import zw.gov.mohcc.impilo.ia.core.AttestationOutcome;
import zw.gov.mohcc.impilo.ia.core.AttestationType;

@Entity
@Table(name = "attestations", schema = "ia")
public class AttestationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "attestation_type", nullable = false)
    private AttestationType attestationType;

    @Column(name = "device_fingerprint")
    private String deviceFingerprint;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "evidence", nullable = false, columnDefinition = "jsonb")
    private String evidence = "{}";

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false)
    private AttestationOutcome outcome = AttestationOutcome.PENDING;

    @Column(name = "confidence", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence = BigDecimal.ZERO;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "recorded_by", nullable = false)
    private String recordedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public AttestationType getAttestationType() { return attestationType; }
    public void setAttestationType(AttestationType attestationType) { this.attestationType = attestationType; }
    public String getDeviceFingerprint() { return deviceFingerprint; }
    public void setDeviceFingerprint(String deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
    public AttestationOutcome getOutcome() { return outcome; }
    public void setOutcome(AttestationOutcome outcome) { this.outcome = outcome; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
