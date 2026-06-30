package zw.gov.mohcc.impilo.guidance.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A config-driven Nompilo guidance catalogue entry (V004). Replaces the cards that used to be
 * hardcoded in the BFF/UI. Each item is applicable to a route / user-type / role / trust level and
 * routes to a sovereign owner — Nompilo guides, it never owns the underlying transaction.
 */
@Entity
@Table(name = "guidance_item", schema = "guidance")
public class GuidanceItemEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false) private String tenantId = "national-spine";
    @Column(name = "guidance_key", nullable = false, length = 128) private String guidanceKey;
    @Column(nullable = false, length = 64) private String domain;
    @Column(name = "guidance_type", nullable = false, length = 48) private String guidanceType;

    @Column(name = "applies_route") private String appliesRoute;
    @Column(name = "applies_user_type", length = 32) private String appliesUserType;
    @Column(name = "applies_role", length = 64) private String appliesRole;
    @Column(name = "min_trust_loa") private Integer minTrustLoa;
    @Column(name = "trigger_condition", length = 128) private String triggerCondition;

    @Column(nullable = false) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String body;
    @Column(name = "cta_label", length = 128) private String ctaLabel;
    @Column(name = "cta_route") private String ctaRoute;

    @Column(name = "owner_service", length = 64) private String ownerService;
    @Column(name = "required_policy", length = 128) private String requiredPolicy;
    @Column(name = "khuluma_follow_up", nullable = false) private boolean khulumaFollowUp = false;
    @Column(name = "fundo_content_ref") private String fundoContentRef;
    @Column(nullable = false, length = 16) private String severity = "INFO";
    @Column(nullable = false) private int priority = 100;
    @Column(name = "audit_required", nullable = false) private boolean auditRequired = false;
    @Column(name = "i18n_key", length = 128) private String i18nKey;
    @Column(name = "accessibility_note") private String accessibilityNote;
    @Column(nullable = false, length = 16) private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getGuidanceKey() { return guidanceKey; }
    public void setGuidanceKey(String guidanceKey) { this.guidanceKey = guidanceKey; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getGuidanceType() { return guidanceType; }
    public void setGuidanceType(String guidanceType) { this.guidanceType = guidanceType; }
    public String getAppliesRoute() { return appliesRoute; }
    public void setAppliesRoute(String appliesRoute) { this.appliesRoute = appliesRoute; }
    public String getAppliesUserType() { return appliesUserType; }
    public void setAppliesUserType(String appliesUserType) { this.appliesUserType = appliesUserType; }
    public String getAppliesRole() { return appliesRole; }
    public void setAppliesRole(String appliesRole) { this.appliesRole = appliesRole; }
    public Integer getMinTrustLoa() { return minTrustLoa; }
    public void setMinTrustLoa(Integer minTrustLoa) { this.minTrustLoa = minTrustLoa; }
    public String getTriggerCondition() { return triggerCondition; }
    public void setTriggerCondition(String triggerCondition) { this.triggerCondition = triggerCondition; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getCtaLabel() { return ctaLabel; }
    public void setCtaLabel(String ctaLabel) { this.ctaLabel = ctaLabel; }
    public String getCtaRoute() { return ctaRoute; }
    public void setCtaRoute(String ctaRoute) { this.ctaRoute = ctaRoute; }
    public String getOwnerService() { return ownerService; }
    public void setOwnerService(String ownerService) { this.ownerService = ownerService; }
    public String getRequiredPolicy() { return requiredPolicy; }
    public void setRequiredPolicy(String requiredPolicy) { this.requiredPolicy = requiredPolicy; }
    public boolean isKhulumaFollowUp() { return khulumaFollowUp; }
    public void setKhulumaFollowUp(boolean khulumaFollowUp) { this.khulumaFollowUp = khulumaFollowUp; }
    public String getFundoContentRef() { return fundoContentRef; }
    public void setFundoContentRef(String fundoContentRef) { this.fundoContentRef = fundoContentRef; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public boolean isAuditRequired() { return auditRequired; }
    public void setAuditRequired(boolean auditRequired) { this.auditRequired = auditRequired; }
    public String getI18nKey() { return i18nKey; }
    public void setI18nKey(String i18nKey) { this.i18nKey = i18nKey; }
    public String getAccessibilityNote() { return accessibilityNote; }
    public void setAccessibilityNote(String accessibilityNote) { this.accessibilityNote = accessibilityNote; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
