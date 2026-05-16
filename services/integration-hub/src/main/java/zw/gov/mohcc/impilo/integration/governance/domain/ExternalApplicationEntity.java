package zw.gov.mohcc.impilo.integration.governance.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ih_external_applications")
public class ExternalApplicationEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "app_code", length = 128, nullable = false)
    private String appCode;

    @Column(name = "name", length = 256, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "publisher_id", length = 64, nullable = false)
    private String publisherId;

    @Column(name = "publisher_name", length = 256, nullable = false)
    private String publisherName;

    @Column(name = "technical_contact_name", length = 256)
    private String technicalContactName;

    @Column(name = "technical_contact_email", length = 256)
    private String technicalContactEmail;

    @Column(name = "technical_contact_phone", length = 64)
    private String technicalContactPhone;

    @Column(name = "data_protection_contact_name", length = 256)
    private String dataProtectionContactName;

    @Column(name = "data_protection_contact_email", length = 256)
    private String dataProtectionContactEmail;

    @Column(name = "status", length = 32, nullable = false)
    private String status = "REGISTERED";

    @Column(name = "environment", length = 16, nullable = false)
    private String environment;

    @Column(name = "integration_category", length = 64, nullable = false)
    private String integrationCategory;

    @Column(name = "risk_classification", length = 16, nullable = false)
    private String riskClassification = "STANDARD";

    @Column(name = "authentication_method", length = 64, nullable = false)
    private String authenticationMethod;

    @Column(name = "oauth_client_id", length = 256)
    private String oauthClientId;

    @Column(name = "oauth_client_secret_ref", length = 256)
    private String oauthClientSecretRef;

    @Column(name = "mtls_certificate_fingerprint", length = 256)
    private String mtlsCertificateFingerprint;

    @Column(name = "ip_allowlist", columnDefinition = "TEXT")
    private String ipAllowlistCsv;

    @Column(name = "allowed_tenants", columnDefinition = "TEXT", nullable = false)
    private String allowedTenantsCsv;

    @Column(name = "allowed_facilities", columnDefinition = "TEXT")
    private String allowedFacilitiesCsv;

    @Column(name = "rate_limit_rpm")
    private Integer rateLimitRpm;

    @Column(name = "rate_limit_burst")
    private Integer rateLimitBurst;

    @Column(name = "credential_expires_at")
    private OffsetDateTime credentialExpiresAt;

    @Column(name = "registered_at", nullable = false)
    private OffsetDateTime registeredAt;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "approved_by", length = 64)
    private String approvedBy;

    @Column(name = "suspended_at")
    private OffsetDateTime suspendedAt;

    @Column(name = "suspended_reason", columnDefinition = "TEXT")
    private String suspendedReason;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "pod_id", length = 64, nullable = false)
    private String podId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();
        if (registeredAt == null) registeredAt = now;
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }

    // ── Getters / Setters ───────────────────────────────────────────────────
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAppCode() { return appCode; }
    public void setAppCode(String appCode) { this.appCode = appCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPublisherId() { return publisherId; }
    public void setPublisherId(String publisherId) { this.publisherId = publisherId; }
    public String getPublisherName() { return publisherName; }
    public void setPublisherName(String publisherName) { this.publisherName = publisherName; }
    public String getTechnicalContactName() { return technicalContactName; }
    public void setTechnicalContactName(String v) { this.technicalContactName = v; }
    public String getTechnicalContactEmail() { return technicalContactEmail; }
    public void setTechnicalContactEmail(String v) { this.technicalContactEmail = v; }
    public String getTechnicalContactPhone() { return technicalContactPhone; }
    public void setTechnicalContactPhone(String v) { this.technicalContactPhone = v; }
    public String getDataProtectionContactName() { return dataProtectionContactName; }
    public void setDataProtectionContactName(String v) { this.dataProtectionContactName = v; }
    public String getDataProtectionContactEmail() { return dataProtectionContactEmail; }
    public void setDataProtectionContactEmail(String v) { this.dataProtectionContactEmail = v; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getIntegrationCategory() { return integrationCategory; }
    public void setIntegrationCategory(String v) { this.integrationCategory = v; }
    public String getRiskClassification() { return riskClassification; }
    public void setRiskClassification(String v) { this.riskClassification = v; }
    public String getAuthenticationMethod() { return authenticationMethod; }
    public void setAuthenticationMethod(String v) { this.authenticationMethod = v; }
    public String getOauthClientId() { return oauthClientId; }
    public void setOauthClientId(String v) { this.oauthClientId = v; }
    public String getOauthClientSecretRef() { return oauthClientSecretRef; }
    public void setOauthClientSecretRef(String v) { this.oauthClientSecretRef = v; }
    public String getMtlsCertificateFingerprint() { return mtlsCertificateFingerprint; }
    public void setMtlsCertificateFingerprint(String v) { this.mtlsCertificateFingerprint = v; }
    public String getIpAllowlistCsv() { return ipAllowlistCsv; }
    public void setIpAllowlistCsv(String v) { this.ipAllowlistCsv = v; }
    public String getAllowedTenantsCsv() { return allowedTenantsCsv; }
    public void setAllowedTenantsCsv(String v) { this.allowedTenantsCsv = v; }
    public String getAllowedFacilitiesCsv() { return allowedFacilitiesCsv; }
    public void setAllowedFacilitiesCsv(String v) { this.allowedFacilitiesCsv = v; }
    public Integer getRateLimitRpm() { return rateLimitRpm; }
    public void setRateLimitRpm(Integer v) { this.rateLimitRpm = v; }
    public Integer getRateLimitBurst() { return rateLimitBurst; }
    public void setRateLimitBurst(Integer v) { this.rateLimitBurst = v; }
    public OffsetDateTime getCredentialExpiresAt() { return credentialExpiresAt; }
    public void setCredentialExpiresAt(OffsetDateTime v) { this.credentialExpiresAt = v; }
    public OffsetDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(OffsetDateTime v) { this.registeredAt = v; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(OffsetDateTime v) { this.approvedAt = v; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String v) { this.approvedBy = v; }
    public OffsetDateTime getSuspendedAt() { return suspendedAt; }
    public void setSuspendedAt(OffsetDateTime v) { this.suspendedAt = v; }
    public String getSuspendedReason() { return suspendedReason; }
    public void setSuspendedReason(String v) { this.suspendedReason = v; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(OffsetDateTime v) { this.revokedAt = v; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getPodId() { return podId; }
    public void setPodId(String podId) { this.podId = podId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
