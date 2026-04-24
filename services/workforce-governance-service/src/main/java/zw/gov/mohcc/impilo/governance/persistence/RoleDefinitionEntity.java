package zw.gov.mohcc.impilo.governance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wgv_role_definition")
public class RoleDefinitionEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "role_code", nullable = false, length = 64)
    private String roleCode;

    @Column(name = "name", nullable = false, length = 512)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "role_category", nullable = false, length = 64)
    private String roleCategory;

    @Column(name = "role_level", nullable = false, length = 64)
    private String roleLevel;

    @Column(name = "allowed_target_types", nullable = false, columnDefinition = "TEXT")
    private String allowedTargetTypes;

    @Column(name = "requires_provider_flag", nullable = false)
    private boolean requiresProviderFlag;

    @Column(name = "requires_professional_standing_flag", nullable = false)
    private boolean requiresProfessionalStandingFlag;

    @Column(name = "special_governance_flag", nullable = false)
    private boolean specialGovernanceFlag;

    @Column(name = "active_flag", nullable = false)
    private boolean activeFlag = true;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RoleDefinitionEntity() {}

    public RoleDefinitionEntity(UUID id, UUID tenantId, String roleCode, String name, String roleCategory,
                                String roleLevel, String allowedTargetTypes) {
        this.id = id;
        this.tenantId = tenantId;
        this.roleCode = roleCode;
        this.name = name;
        this.roleCategory = roleCategory;
        this.roleLevel = roleLevel;
        this.allowedTargetTypes = allowedTargetTypes;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getRoleCode() { return roleCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; touch(); }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; touch(); }
    public String getRoleCategory() { return roleCategory; }
    public String getRoleLevel() { return roleLevel; }
    public String getAllowedTargetTypes() { return allowedTargetTypes; }
    public boolean isRequiresProviderFlag() { return requiresProviderFlag; }
    public void setRequiresProviderFlag(boolean requiresProviderFlag) { this.requiresProviderFlag = requiresProviderFlag; touch(); }
    public boolean isRequiresProfessionalStandingFlag() { return requiresProfessionalStandingFlag; }
    public void setRequiresProfessionalStandingFlag(boolean requiresProfessionalStandingFlag) {
        this.requiresProfessionalStandingFlag = requiresProfessionalStandingFlag; touch();
    }
    public boolean isSpecialGovernanceFlag() { return specialGovernanceFlag; }
    public void setSpecialGovernanceFlag(boolean specialGovernanceFlag) { this.specialGovernanceFlag = specialGovernanceFlag; touch(); }
    public boolean isActiveFlag() { return activeFlag; }
    public void setActiveFlag(boolean activeFlag) { this.activeFlag = activeFlag; touch(); }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; touch(); }

    private void touch() { this.updatedAt = Instant.now(); }
}
