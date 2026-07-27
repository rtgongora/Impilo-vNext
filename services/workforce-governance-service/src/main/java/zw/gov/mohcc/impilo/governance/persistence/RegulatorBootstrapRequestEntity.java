package zw.gov.mohcc.impilo.governance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A request to found a regulator — to appoint its first administrator when there is nobody there
 * yet to do so (V013, RB-3).
 *
 * <p>This is the domain record; the four-eyes vehicle is the PLATFORM_ACTION access request it
 * points at ({@link #accessRequestId}). Activation emits an event org-registry consumes to create
 * and verify the founding appointment — this service never writes the appointment itself.</p>
 */
@Entity
@Table(name = "wgv_regulator_bootstrap_request")
public class RegulatorBootstrapRequestEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "public_id", nullable = false, length = 32)
    private String publicId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "claimant_health_id", nullable = false, length = 128)
    private String claimantHealthId;

    @Column(name = "claimed_role_code", nullable = false, length = 48)
    private String claimedRoleCode;

    @Column(name = "evidence_ref", length = 512)
    private String evidenceRef;

    @Column(name = "authoritative_check_result", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private Map<String, Object> authoritativeCheckResult;

    @Column(name = "access_request_id")
    private UUID accessRequestId;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "SUBMITTED";

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }

    public String getClaimantHealthId() { return claimantHealthId; }
    public void setClaimantHealthId(String claimantHealthId) { this.claimantHealthId = claimantHealthId; }

    public String getClaimedRoleCode() { return claimedRoleCode; }
    public void setClaimedRoleCode(String claimedRoleCode) { this.claimedRoleCode = claimedRoleCode; }

    public String getEvidenceRef() { return evidenceRef; }
    public void setEvidenceRef(String evidenceRef) { this.evidenceRef = evidenceRef; }

    public Map<String, Object> getAuthoritativeCheckResult() { return authoritativeCheckResult; }
    public void setAuthoritativeCheckResult(Map<String, Object> v) { this.authoritativeCheckResult = v; }

    public UUID getAccessRequestId() { return accessRequestId; }
    public void setAccessRequestId(UUID accessRequestId) { this.accessRequestId = accessRequestId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public Instant getActivatedAt() { return activatedAt; }
    public void setActivatedAt(Instant activatedAt) { this.activatedAt = activatedAt; }
}
