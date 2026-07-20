package zw.gov.mohcc.impilo.ubomi.integration.rg;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Ledger row for one outbound RG interaction (V005). See the migration header
 * for the status lifecycle. What this entity <b>cannot</b> hold is the point:
 * an opaque {@link #civilIdentityToken}, a status, and a one-way
 * {@link #referenceHash} — never the full RG record and never the raw
 * identifier.
 */
@Entity
@Table(name = "rg_verification", schema = "ubomi")
public class RgVerificationEntity {

    /** RG interaction statuses. */
    public static final String RG_PENDING = "RG_PENDING";
    public static final String RG_VERIFIED = "RG_VERIFIED";
    public static final String RG_NO_MATCH = "RG_NO_MATCH";
    public static final String RG_CONFIRMED = "RG_CONFIRMED";
    public static final String RG_DISPUTED = "RG_DISPUTED";
    public static final String RG_UNAVAILABLE = "RG_UNAVAILABLE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_ref", nullable = false, length = 128)
    private String subjectRef;

    @Column(name = "verification_type", nullable = false, length = 40)
    private String verificationType;

    @Column(name = "reference_hash", length = 64)
    private String referenceHash;

    @Column(name = "status", nullable = false, length = 24)
    private String status = RG_PENDING;

    @Column(name = "civil_identity_token", length = 512)
    private String civilIdentityToken;

    @Column(name = "source_outbox_id")
    private Long sourceOutboxId;

    @Column(name = "business_key", nullable = false, length = 256)
    private String businessKey;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "last_attempt_at")
    private OffsetDateTime lastAttemptAt;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getSubjectRef() { return subjectRef; }
    public void setSubjectRef(String subjectRef) { this.subjectRef = subjectRef; }
    public String getVerificationType() { return verificationType; }
    public void setVerificationType(String verificationType) { this.verificationType = verificationType; }
    public String getReferenceHash() { return referenceHash; }
    public void setReferenceHash(String referenceHash) { this.referenceHash = referenceHash; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCivilIdentityToken() { return civilIdentityToken; }
    public void setCivilIdentityToken(String civilIdentityToken) { this.civilIdentityToken = civilIdentityToken; }
    public Long getSourceOutboxId() { return sourceOutboxId; }
    public void setSourceOutboxId(Long sourceOutboxId) { this.sourceOutboxId = sourceOutboxId; }
    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public OffsetDateTime getLastAttemptAt() { return lastAttemptAt; }
    public void setLastAttemptAt(OffsetDateTime lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; }
    public OffsetDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(OffsetDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    /** Settable for reconcile TTL reasoning / test reconstruction; @PrePersist wins on first save. */
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
