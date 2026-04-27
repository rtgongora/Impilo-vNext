package zw.gov.mohcc.impilo.mvumo.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consent_template", schema = "mvumo")
public class ConsentTemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "template_key", nullable = false, length = 160)
    private String templateKey;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 16)
    private String language = "en";

    @Column(name = "consent_type", nullable = false, length = 96)
    private String consentType;

    @Column(name = "required_assurance", nullable = false, length = 32)
    private String requiredAssurance;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(name = "body_markdown", nullable = false, columnDefinition = "text")
    private String bodyMarkdown;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_methods", nullable = false, columnDefinition = "jsonb")
    private String allowedMethodsJson = "[]";

    @Column(name = "zibo_concept_code", length = 128)
    private String ziboConceptCode;

    @Column(name = "remote_allowed", nullable = false)
    private boolean remoteAllowed = true;

    @Column(name = "offline_allowed", nullable = false)
    private boolean offlineAllowed = true;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (effectiveFrom == null) {
            effectiveFrom = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getTemplateKey() {
        return templateKey;
    }

    public void setTemplateKey(String templateKey) {
        this.templateKey = templateKey;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getConsentType() {
        return consentType;
    }

    public void setConsentType(String consentType) {
        this.consentType = consentType;
    }

    public String getRequiredAssurance() {
        return requiredAssurance;
    }

    public void setRequiredAssurance(String requiredAssurance) {
        this.requiredAssurance = requiredAssurance;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBodyMarkdown() {
        return bodyMarkdown;
    }

    public void setBodyMarkdown(String bodyMarkdown) {
        this.bodyMarkdown = bodyMarkdown;
    }

    public String getAllowedMethodsJson() {
        return allowedMethodsJson;
    }

    public void setAllowedMethodsJson(String allowedMethodsJson) {
        this.allowedMethodsJson = allowedMethodsJson;
    }

    public String getZiboConceptCode() {
        return ziboConceptCode;
    }

    public void setZiboConceptCode(String ziboConceptCode) {
        this.ziboConceptCode = ziboConceptCode;
    }

    public boolean isRemoteAllowed() {
        return remoteAllowed;
    }

    public void setRemoteAllowed(boolean remoteAllowed) {
        this.remoteAllowed = remoteAllowed;
    }

    public boolean isOfflineAllowed() {
        return offlineAllowed;
    }

    public void setOfflineAllowed(boolean offlineAllowed) {
        this.offlineAllowed = offlineAllowed;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(Instant effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public Instant getRetiredAt() {
        return retiredAt;
    }

    public void setRetiredAt(Instant retiredAt) {
        this.retiredAt = retiredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
