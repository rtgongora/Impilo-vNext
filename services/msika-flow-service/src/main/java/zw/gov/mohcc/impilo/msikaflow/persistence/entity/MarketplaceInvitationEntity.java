package zw.gov.mohcc.impilo.msikaflow.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.msikaflow.domain.InvitationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An invitation to offer, issued to one eligible vendor for one request round
 * (OF-B4/OF-B5, Vol II §11.1).
 *
 * <p>{@code eligibilitySnapshotJson} is the six-precondition evaluation result
 * at invitation time (§11.3 — evaluated at invitation AND re-evaluated at
 * commitment step 4), stored for audit.</p>
 */
@Entity
@Table(name = "mf_marketplace_invitations")
public class MarketplaceInvitationEntity {

    @Id
    @Column(name = "invitation_id", nullable = false, length = 26)
    private String invitationId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "request_id", nullable = false, length = 26)
    private String requestId;

    @Column(name = "vendor_id", nullable = false, length = 26)
    private String vendorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private InvitationStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "eligibility_snapshot_json", columnDefinition = "jsonb")
    private String eligibilitySnapshotJson;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
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

    public String getInvitationId() { return invitationId; }
    public void setInvitationId(String invitationId) { this.invitationId = invitationId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getVendorId() { return vendorId; }
    public void setVendorId(String vendorId) { this.vendorId = vendorId; }

    public InvitationStatus getStatus() { return status; }
    public void setStatus(InvitationStatus status) { this.status = status; }

    public String getEligibilitySnapshotJson() { return eligibilitySnapshotJson; }
    public void setEligibilitySnapshotJson(String eligibilitySnapshotJson) { this.eligibilitySnapshotJson = eligibilitySnapshotJson; }

    public OffsetDateTime getSentAt() { return sentAt; }
    public void setSentAt(OffsetDateTime sentAt) { this.sentAt = sentAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
