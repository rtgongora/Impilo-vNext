package zw.gov.mohcc.impilo.support.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Case-linked, time-bound, approved elevation to diagnostic access. The ONLY
 * path by which a support session may ever touch anything beyond
 * platform/config/device/integration signals (support-service SoR).
 */
@Entity
@Table(name = "sup_support_elevated_access_grant")
public class SupportElevatedAccessGrantEntity {
    @Id @Column(name = "grant_id") private UUID grantId;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "support_assignment_id", nullable = false) private UUID supportAssignmentId;
    @Column(name = "ticket_id", nullable = false) private UUID ticketId;
    @Column(name = "stated_purpose", nullable = false, columnDefinition = "TEXT") private String statedPurpose;
    @Column(name = "minimum_scope", nullable = false, columnDefinition = "TEXT") private String minimumScope;
    @Column(name = "requested_by", nullable = false) private String requestedBy;
    @Column(name = "approved_by") private String approvedBy;
    @Column(nullable = false, length = 32) private String status = "PENDING_APPROVAL";
    @Column(name = "granted_at") private OffsetDateTime grantedAt;
    @Column(name = "expires_at") private OffsetDateTime expiresAt;
    @Column(name = "revoked_at") private OffsetDateTime revokedAt;
    @Column(name = "revoked_by") private String revokedBy;
    @Column(name = "revocation_reason", columnDefinition = "TEXT") private String revocationReason;
    @Column(name = "visible_indicator", nullable = false) private boolean visibleIndicator = true;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    protected SupportElevatedAccessGrantEntity() {}

    public SupportElevatedAccessGrantEntity(UUID grantId, UUID tenantId, UUID supportAssignmentId, UUID ticketId,
                                             String statedPurpose, String minimumScope, String requestedBy) {
        this.grantId = grantId;
        this.tenantId = tenantId;
        this.supportAssignmentId = supportAssignmentId;
        this.ticketId = ticketId;
        this.statedPurpose = statedPurpose;
        this.minimumScope = minimumScope;
        this.requestedBy = requestedBy;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getGrantId() { return grantId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getSupportAssignmentId() { return supportAssignmentId; }
    public UUID getTicketId() { return ticketId; }
    public String getStatedPurpose() { return statedPurpose; }
    public String getMinimumScope() { return minimumScope; }
    public String getRequestedBy() { return requestedBy; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String v) { this.approvedBy = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; this.updatedAt = OffsetDateTime.now(); }
    public OffsetDateTime getGrantedAt() { return grantedAt; }
    public void setGrantedAt(OffsetDateTime v) { this.grantedAt = v; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime v) { this.expiresAt = v; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(OffsetDateTime v) { this.revokedAt = v; }
    public String getRevokedBy() { return revokedBy; }
    public void setRevokedBy(String v) { this.revokedBy = v; }
    public String getRevocationReason() { return revocationReason; }
    public void setRevocationReason(String v) { this.revocationReason = v; }
    public boolean isVisibleIndicator() { return visibleIndicator; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public boolean isCurrentlyActive() {
        return "ACTIVE".equals(status) && expiresAt != null && expiresAt.isAfter(OffsetDateTime.now());
    }
}
