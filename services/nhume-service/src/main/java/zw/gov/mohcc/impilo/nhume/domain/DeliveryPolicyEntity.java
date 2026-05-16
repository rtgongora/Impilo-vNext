package zw.gov.mohcc.impilo.nhume.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "nhume_delivery_policies")
public class DeliveryPolicyEntity {

    @Id
    @Column(name = "policy_id", nullable = false, length = 64)
    private String policyId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "delivery_type", nullable = false, length = 48)
    private String deliveryType;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval;

    @Column(name = "requires_payment", nullable = false)
    private boolean requiresPayment;

    @Column(name = "requires_stock_check", nullable = false)
    private boolean requiresStockCheck;

    @Column(name = "requires_identity_verify", nullable = false, length = 32)
    private String requiresIdentityVerify = "NONE";

    @Column(name = "requires_consent", nullable = false)
    private boolean requiresConsent;

    @Column(name = "requires_chain_of_custody", nullable = false)
    private boolean requiresChainOfCustody;

    @Column(name = "cold_chain_required", nullable = false)
    private boolean coldChainRequired;

    @Column(name = "proof_methods_required", nullable = false, columnDefinition = "TEXT")
    private String proofMethodsRequired = "[\"RECIPIENT_SIGNATURE\"]";

    @Column(name = "allowed_modes", nullable = false, columnDefinition = "TEXT")
    private String allowedModes = "[]";

    @Column(name = "allowed_courier_kinds", nullable = false, columnDefinition = "TEXT")
    private String allowedCourierKinds = "[]";

    @Column(name = "notification_set", length = 64)
    private String notificationSet;

    @Column(name = "sla_target_minutes")
    private Integer slaTargetMinutes;

    @Column(name = "escalation_rule", length = 64)
    private String escalationRule;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "is_demo", nullable = false)
    private boolean demo;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public DeliveryPolicyEntity() {}

    public String getPolicyId() { return policyId; }
    public void setPolicyId(String v) { this.policyId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getDeliveryType() { return deliveryType; }
    public void setDeliveryType(String v) { this.deliveryType = v; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String v) { this.displayName = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public boolean isRequiresApproval() { return requiresApproval; }
    public void setRequiresApproval(boolean v) { this.requiresApproval = v; }
    public boolean isRequiresPayment() { return requiresPayment; }
    public void setRequiresPayment(boolean v) { this.requiresPayment = v; }
    public boolean isRequiresStockCheck() { return requiresStockCheck; }
    public void setRequiresStockCheck(boolean v) { this.requiresStockCheck = v; }
    public String getRequiresIdentityVerify() { return requiresIdentityVerify; }
    public void setRequiresIdentityVerify(String v) { this.requiresIdentityVerify = v; }
    public boolean isRequiresConsent() { return requiresConsent; }
    public void setRequiresConsent(boolean v) { this.requiresConsent = v; }
    public boolean isRequiresChainOfCustody() { return requiresChainOfCustody; }
    public void setRequiresChainOfCustody(boolean v) { this.requiresChainOfCustody = v; }
    public boolean isColdChainRequired() { return coldChainRequired; }
    public void setColdChainRequired(boolean v) { this.coldChainRequired = v; }
    public String getProofMethodsRequired() { return proofMethodsRequired; }
    public void setProofMethodsRequired(String v) { this.proofMethodsRequired = v; }
    public String getAllowedModes() { return allowedModes; }
    public void setAllowedModes(String v) { this.allowedModes = v; }
    public String getAllowedCourierKinds() { return allowedCourierKinds; }
    public void setAllowedCourierKinds(String v) { this.allowedCourierKinds = v; }
    public String getNotificationSet() { return notificationSet; }
    public void setNotificationSet(String v) { this.notificationSet = v; }
    public Integer getSlaTargetMinutes() { return slaTargetMinutes; }
    public void setSlaTargetMinutes(Integer v) { this.slaTargetMinutes = v; }
    public String getEscalationRule() { return escalationRule; }
    public void setEscalationRule(String v) { this.escalationRule = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
    public boolean isDemo() { return demo; }
    public void setDemo(boolean v) { this.demo = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
