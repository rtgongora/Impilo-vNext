package zw.gov.mohcc.impilo.mvumo.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * An explicit, scoped, time-bounded, revocable relationship letting a delegate act FOR a subject
 * (L5 delegated/assisted access — closes G-CZO-03). Mvumo is the act-of-record; the trust plane
 * (PolicyEngine Step 4.5) authorises each delegated action against it. A consent-backed delegation
 * links the identity-verified grant via {@code backingConsentRequestId}.
 */
@Entity
@Table(name = "delegation_relationship", schema = "mvumo")
public class DelegationRelationshipEntity {

    public enum Status { GRANTED, WITHDRAWN, EXPIRED, SUPERSEDED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "delegator_subject_ref", nullable = false, length = 255)
    private String delegatorSubjectRef;

    @Column(name = "delegate_actor_id", nullable = false, length = 255)
    private String delegateActorId;

    @Column(name = "relationship_type", nullable = false, length = 32)
    private String relationshipType;

    @Column(name = "legal_basis", nullable = false, length = 32)
    private String legalBasis;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String scope = "[]";

    @Column(name = "min_delegate_loa", nullable = false)
    private int minDelegateLoa = 2;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "backing_consent_request_id")
    private UUID backingConsentRequestId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_reason", length = 512)
    private String revokedReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getDelegatorSubjectRef() { return delegatorSubjectRef; }
    public void setDelegatorSubjectRef(String delegatorSubjectRef) { this.delegatorSubjectRef = delegatorSubjectRef; }

    public String getDelegateActorId() { return delegateActorId; }
    public void setDelegateActorId(String delegateActorId) { this.delegateActorId = delegateActorId; }

    public String getRelationshipType() { return relationshipType; }
    public void setRelationshipType(String relationshipType) { this.relationshipType = relationshipType; }

    public String getLegalBasis() { return legalBasis; }
    public void setLegalBasis(String legalBasis) { this.legalBasis = legalBasis; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public int getMinDelegateLoa() { return minDelegateLoa; }
    public void setMinDelegateLoa(int minDelegateLoa) { this.minDelegateLoa = minDelegateLoa; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public UUID getBackingConsentRequestId() { return backingConsentRequestId; }
    public void setBackingConsentRequestId(UUID backingConsentRequestId) { this.backingConsentRequestId = backingConsentRequestId; }

    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public String getRevokedReason() { return revokedReason; }
    public void setRevokedReason(String revokedReason) { this.revokedReason = revokedReason; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
