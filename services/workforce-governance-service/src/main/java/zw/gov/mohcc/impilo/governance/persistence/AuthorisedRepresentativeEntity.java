package zw.gov.mohcc.impilo.governance.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "wgv_authorised_representative")
public class AuthorisedRepresentativeEntity {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "organisation_id", nullable = false)
    private UUID organisationId;
    @Column(name = "user_id", nullable = false)
    private String userId;
    @Column(name = "representative_type", nullable = false)
    private String representativeType;
    @Column(name = "role_template_id", nullable = false)
    private String roleTemplateId;
    @Column(name = "status", nullable = false)
    private String status;
    @Column(name = "effective_date")
    private LocalDate effectiveDate;
    @Column(name = "expiry_date")
    private LocalDate expiryDate;
    @Column(name = "approved_by_user_id")
    private String approvedByUserId;
    @Column(name = "data_responsibility_accepted_at")
    private Instant dataResponsibilityAcceptedAt;
    @Column(name = "audit_metadata", columnDefinition = "TEXT")
    private String auditMetadata;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AuthorisedRepresentativeEntity() {}

    public void init(UUID tenantId, UUID organisationId, String userId, String representativeType, String roleTemplateId, String status) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.organisationId = organisationId;
        this.userId = userId;
        this.representativeType = representativeType;
        this.roleTemplateId = roleTemplateId;
        this.status = status;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.effectiveDate = LocalDate.now();
    }

    public UUID getId() { return id; }
    public String getUserId() { return userId; }
    public String getStatus() { return status; }
    public String getRepresentativeType() { return representativeType; }
    public String getRoleTemplateId() { return roleTemplateId; }
    public String getAuditMetadata() { return auditMetadata; }
    public void setStatus(String status) { this.status = status; touch(); }
    public void setRoleTemplateId(String roleTemplateId) { this.roleTemplateId = roleTemplateId; touch(); }
    public void setApprovedByUserId(String approvedByUserId) { this.approvedByUserId = approvedByUserId; touch(); }
    public void setAuditMetadata(String auditMetadata) { this.auditMetadata = auditMetadata; touch(); }
    public void acceptDataResponsibility() { this.dataResponsibilityAcceptedAt = Instant.now(); touch(); }

    private void touch() { this.updatedAt = Instant.now(); }
}
