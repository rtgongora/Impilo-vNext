package zw.gov.mohcc.impilo.integration.governance.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ih_integration_contracts")
public class IntegrationContractEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "external_app_id", length = 36, nullable = false)
    private String externalAppId;

    @Column(name = "contract_code", length = 128, nullable = false)
    private String contractCode;

    @Column(name = "version", length = 32, nullable = false)
    private String version;

    @Column(name = "integration_category", length = 64, nullable = false)
    private String integrationCategory;

    @Column(name = "environment", length = 16, nullable = false)
    private String environment;

    @Column(name = "effective_from", nullable = false)
    private OffsetDateTime effectiveFrom;

    @Column(name = "effective_until")
    private OffsetDateTime effectiveUntil;

    @Lob
    @Column(name = "allowed_apis", columnDefinition = "TEXT", nullable = false)
    private String allowedApisCsv;

    @Lob
    @Column(name = "allowed_events", columnDefinition = "TEXT")
    private String allowedEventsCsv;

    @Lob
    @Column(name = "allowed_webhook_callbacks", columnDefinition = "TEXT")
    private String allowedWebhookCallbacksCsv;

    @Lob
    @Column(name = "scopes", columnDefinition = "TEXT", nullable = false)
    private String scopesCsv;

    @Lob
    @Column(name = "purpose_of_use", columnDefinition = "TEXT", nullable = false)
    private String purposeOfUseCsv;

    @Column(name = "consent_basis", length = 64)
    private String consentBasis;

    @Column(name = "signature_method", length = 32, nullable = false)
    private String signatureMethod;

    @Column(name = "rate_limit_rpm")
    private Integer rateLimitRpm;

    @Column(name = "rate_limit_burst")
    private Integer rateLimitBurst;

    @Column(name = "error_handling_policy", length = 32)
    private String errorHandlingPolicy;

    @Column(name = "data_retention_days")
    private Integer dataRetentionDays;

    @Column(name = "incident_reporting_hours")
    private Integer incidentReportingHours;

    @Column(name = "status", length = 16, nullable = false)
    private String status = "DRAFT";

    @Column(name = "signed_by_name", length = 256)
    private String signedByName;

    @Column(name = "signed_by_email", length = 256)
    private String signedByEmail;

    @Column(name = "signed_at")
    private OffsetDateTime signedAt;

    @Column(name = "document_ref", length = 512)
    private String documentRef;

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
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getExternalAppId() { return externalAppId; }
    public void setExternalAppId(String v) { this.externalAppId = v; }
    public String getContractCode() { return contractCode; }
    public void setContractCode(String v) { this.contractCode = v; }
    public String getVersion() { return version; }
    public void setVersion(String v) { this.version = v; }
    public String getIntegrationCategory() { return integrationCategory; }
    public void setIntegrationCategory(String v) { this.integrationCategory = v; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String v) { this.environment = v; }
    public OffsetDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(OffsetDateTime v) { this.effectiveFrom = v; }
    public OffsetDateTime getEffectiveUntil() { return effectiveUntil; }
    public void setEffectiveUntil(OffsetDateTime v) { this.effectiveUntil = v; }
    public String getAllowedApisCsv() { return allowedApisCsv; }
    public void setAllowedApisCsv(String v) { this.allowedApisCsv = v; }
    public String getAllowedEventsCsv() { return allowedEventsCsv; }
    public void setAllowedEventsCsv(String v) { this.allowedEventsCsv = v; }
    public String getAllowedWebhookCallbacksCsv() { return allowedWebhookCallbacksCsv; }
    public void setAllowedWebhookCallbacksCsv(String v) { this.allowedWebhookCallbacksCsv = v; }
    public String getScopesCsv() { return scopesCsv; }
    public void setScopesCsv(String v) { this.scopesCsv = v; }
    public String getPurposeOfUseCsv() { return purposeOfUseCsv; }
    public void setPurposeOfUseCsv(String v) { this.purposeOfUseCsv = v; }
    public String getConsentBasis() { return consentBasis; }
    public void setConsentBasis(String v) { this.consentBasis = v; }
    public String getSignatureMethod() { return signatureMethod; }
    public void setSignatureMethod(String v) { this.signatureMethod = v; }
    public Integer getRateLimitRpm() { return rateLimitRpm; }
    public void setRateLimitRpm(Integer v) { this.rateLimitRpm = v; }
    public Integer getRateLimitBurst() { return rateLimitBurst; }
    public void setRateLimitBurst(Integer v) { this.rateLimitBurst = v; }
    public String getErrorHandlingPolicy() { return errorHandlingPolicy; }
    public void setErrorHandlingPolicy(String v) { this.errorHandlingPolicy = v; }
    public Integer getDataRetentionDays() { return dataRetentionDays; }
    public void setDataRetentionDays(Integer v) { this.dataRetentionDays = v; }
    public Integer getIncidentReportingHours() { return incidentReportingHours; }
    public void setIncidentReportingHours(Integer v) { this.incidentReportingHours = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getSignedByName() { return signedByName; }
    public void setSignedByName(String v) { this.signedByName = v; }
    public String getSignedByEmail() { return signedByEmail; }
    public void setSignedByEmail(String v) { this.signedByEmail = v; }
    public OffsetDateTime getSignedAt() { return signedAt; }
    public void setSignedAt(OffsetDateTime v) { this.signedAt = v; }
    public String getDocumentRef() { return documentRef; }
    public void setDocumentRef(String v) { this.documentRef = v; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String v) { this.tenantId = v; }
    public String getPodId() { return podId; }
    public void setPodId(String v) { this.podId = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
