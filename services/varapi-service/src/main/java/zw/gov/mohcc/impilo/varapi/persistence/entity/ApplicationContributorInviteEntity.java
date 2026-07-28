package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * An invitation for someone other than the applicant to complete a NAMED part of an application
 * (NCZ-W1C) — a training institution confirming enrolment, and nothing else.
 *
 * <p>{@code scopeSections} is the whole point. The database refuses an invite that names no
 * section, wildcards them, or names one the application does not have. Those guards stop a bad
 * INVITE; only the service stops a bad READ, which is why every contributor read is filtered
 * through this scope rather than trusted to the caller.</p>
 */
@Entity
@Table(name = "application_contributor_invite", schema = "varapi")
public class ApplicationContributorInviteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "contributor_kind", nullable = false, length = 48)
    private String contributorKind;

    @Column(name = "contributor_org_id")
    private UUID contributorOrgId;

    @Column(name = "contributor_ref", length = 128)
    private String contributorRef;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.ARRAY)
    @Column(name = "scope_sections", nullable = false, columnDefinition = "text[]")
    private String[] scopeSections;

    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 24)
    private String status = "INVITED";

    @Column(name = "invited_by", length = 128)
    private String invitedBy;

    @Column(name = "invited_at", nullable = false)
    private Instant invitedAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "declined_reason")
    private String declinedReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public String getContributorKind() { return contributorKind; }
    public void setContributorKind(String contributorKind) { this.contributorKind = contributorKind; }
    public UUID getContributorOrgId() { return contributorOrgId; }
    public void setContributorOrgId(UUID contributorOrgId) { this.contributorOrgId = contributorOrgId; }
    public String getContributorRef() { return contributorRef; }
    public void setContributorRef(String contributorRef) { this.contributorRef = contributorRef; }
    public String[] getScopeSections() { return scopeSections; }
    public void setScopeSections(String[] scopeSections) { this.scopeSections = scopeSections; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getInvitedBy() { return invitedBy; }
    public void setInvitedBy(String invitedBy) { this.invitedBy = invitedBy; }
    public Instant getInvitedAt() { return invitedAt; }
    public void setInvitedAt(Instant invitedAt) { this.invitedAt = invitedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getDeclinedReason() { return declinedReason; }
    public void setDeclinedReason(String declinedReason) { this.declinedReason = declinedReason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
