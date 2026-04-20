package zw.gov.mohcc.impilo.pharmacy.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;
import zw.gov.mohcc.impilo.pharmacy.domain.SubstitutionRuleType;

/**
 * Defines a drug substitution rule for the pharmacy dispensing workflow.
 * Rules map a source drug code to a permissible target, optionally requiring step-up authorization.
 */
@Entity
@Table(name = "rx_substitution_rules")
public class SubstitutionRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private SubstitutionRuleType ruleType;

    @Column(name = "source_code", nullable = false)
    private String sourceCode;

    @Column(name = "target_code", nullable = false)
    private String targetCode;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "rule_json", columnDefinition = "jsonb")
    private String ruleJson;

    @Column(name = "requires_step_up", nullable = false)
    private boolean requiresStepUp;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // Getters and setters

    public UUID getRuleId() { return ruleId; }
    public void setRuleId(UUID ruleId) { this.ruleId = ruleId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public SubstitutionRuleType getRuleType() { return ruleType; }
    public void setRuleType(SubstitutionRuleType ruleType) { this.ruleType = ruleType; }

    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }

    public String getTargetCode() { return targetCode; }
    public void setTargetCode(String targetCode) { this.targetCode = targetCode; }

    public String getRuleJson() { return ruleJson; }
    public void setRuleJson(String ruleJson) { this.ruleJson = ruleJson; }

    public boolean isRequiresStepUp() { return requiresStepUp; }
    public void setRequiresStepUp(boolean requiresStepUp) { this.requiresStepUp = requiresStepUp; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
