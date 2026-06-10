package zw.gov.mohcc.impilo.governance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wgv_organisation_membership")
public class OrganisationMembershipEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "organisation_id", nullable = false)
    private UUID organisationId;

    @Column(name = "subject_id", nullable = false)
    private String subjectId;

    @Column(name = "subject_type", nullable = false, length = 32)
    private String subjectType = "USER";

    @Column(name = "role_template", length = 128)
    private String roleTemplate;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public OrganisationMembershipEntity() {}

    public void init(UUID tenantId, UUID organisationId, String subjectId, String subjectType, String roleTemplate, String status) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.organisationId = organisationId;
        this.subjectId = subjectId;
        this.subjectType = subjectType != null ? subjectType : "USER";
        this.roleTemplate = roleTemplate;
        this.status = status;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getOrganisationId() { return organisationId; }
    public String getSubjectId() { return subjectId; }
    public String getSubjectType() { return subjectType; }
    public String getRoleTemplate() { return roleTemplate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; touch(); }
    public void setRoleTemplate(String roleTemplate) { this.roleTemplate = roleTemplate; touch(); }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; touch(); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
