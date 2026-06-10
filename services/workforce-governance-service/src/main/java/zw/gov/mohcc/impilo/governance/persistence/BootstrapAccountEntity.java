package zw.gov.mohcc.impilo.governance.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wgv_bootstrap_account")
public class BootstrapAccountEntity {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "bootstrap_method", nullable = false)
    private String bootstrapMethod;
    @Column(name = "status", nullable = false)
    private String status;
    @Column(name = "sovereign_organisation_id")
    private UUID sovereignOrganisationId;
    @Column(name = "nominated_email", nullable = false)
    private String nominatedEmail;
    @Column(name = "nominated_full_name")
    private String nominatedFullName;
    @Column(name = "approval_reference")
    private String approvalReference;
    @Column(name = "keycloak_user_id")
    private String keycloakUserId;
    @Column(name = "invitation_id")
    private String invitationId;
    @Column(name = "role_expires_at")
    private Instant roleExpiresAt;
    @Column(name = "activated_at")
    private Instant activatedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BootstrapAccountEntity() {}

    public void init(UUID tenantId, String method, String email, String fullName, UUID orgId) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.bootstrapMethod = method;
        this.status = "PENDING";
        this.nominatedEmail = email;
        this.nominatedFullName = fullName;
        this.sovereignOrganisationId = orgId;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void overrideId(UUID id) { this.id = id; }
    public UUID getId() { return id; }
    public String getNominatedEmail() { return nominatedEmail; }
    public void setStatus(String status) { this.status = status; touch(); }
    public void setKeycloakUserId(String keycloakUserId) { this.keycloakUserId = keycloakUserId; touch(); }
    public void setActivatedAt(Instant activatedAt) { this.activatedAt = activatedAt; touch(); }
    public void setApprovalReference(String approvalReference) { this.approvalReference = approvalReference; touch(); }
    public String getKeycloakUserId() { return keycloakUserId; }

    private void touch() { this.updatedAt = Instant.now(); }
}
