package zw.gov.mohcc.impilo.tshepo.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consent_directive", schema = "tshepo")
public class ConsentDirectiveEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_cpid", nullable = false)
    private String subjectCpid;

    @Column(name = "grantor_id", nullable = false)
    private String grantorId;

    @Column(name = "grantee_scope", nullable = false)
    private String granteeScope;

    @Column(name = "purpose_of_use", nullable = false)
    private String purposeOfUse;

    @Column(name = "resource_scope")
    private String resourceScope;

    @Column(nullable = false)
    private String status;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(columnDefinition = "jsonb")
    private String provenance;

    // --- Getters and Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String subjectCpid) { this.subjectCpid = subjectCpid; }
    public String getGrantorId() { return grantorId; }
    public void setGrantorId(String grantorId) { this.grantorId = grantorId; }
    public String getGranteeScope() { return granteeScope; }
    public void setGranteeScope(String granteeScope) { this.granteeScope = granteeScope; }
    public String getPurposeOfUse() { return purposeOfUse; }
    public void setPurposeOfUse(String purposeOfUse) { this.purposeOfUse = purposeOfUse; }
    public String getResourceScope() { return resourceScope; }
    public void setResourceScope(String resourceScope) { this.resourceScope = resourceScope; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getValidFrom() { return validFrom; }
    public void setValidFrom(Instant validFrom) { this.validFrom = validFrom; }
    public Instant getValidTo() { return validTo; }
    public void setValidTo(Instant validTo) { this.validTo = validTo; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public String getProvenance() { return provenance; }
    public void setProvenance(String provenance) { this.provenance = provenance; }
}
