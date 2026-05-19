package zw.gov.mohcc.impilo.msikaapps.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ma_marketplace_items")
public class MarketplaceItemEntity {

    @Id
    private String id = UUID.randomUUID().toString();

    @Column(name = "item_code", nullable = false, unique = true) private String itemCode;
    @Column(nullable = false) private String name;
    @Column(nullable = false, columnDefinition = "TEXT") private String description;
    @Column(nullable = false) private String type;
    @Column(nullable = false) private String category;
    @Column(name = "publisher_id", nullable = false) private String publisherId;
    @Column(nullable = false) private String version;
    @Column(name = "compatibility_version", nullable = false) private String compatibilityVersion;
    @Column(name = "supported_environments", nullable = false) private String supportedEnvironments;
    @Column(nullable = false) private String visibility = "PUBLIC";
    @Column(name = "icon_ref") private String iconRef;
    @Column(name = "documentation_url") private String documentationUrl;
    @Column(name = "manifest_ref", nullable = false) private String manifestRef;
    @Column(name = "security_classification", nullable = false) private String securityClassification = "STANDARD";
    @Column(name = "data_protection_classification", nullable = false) private String dataProtectionClassification = "NO_PHI";
    @Column(name = "clinical_safety_classification") private String clinicalSafetyClassification;
    @Column(name = "approval_status", nullable = false) private String approvalStatus = "APPROVED";
    @Column(name = "certification_status") private String certificationStatus;
    @Column(name = "regulatory_status") private String regulatoryStatus;
    @Column(name = "pricing_model")   private String pricingModel;
    @Column(name = "licensing_model") private String licensingModel;
    @Column(name = "required_scopes",          columnDefinition = "TEXT") private String requiredScopesCsv;
    @Column(name = "required_events",          columnDefinition = "TEXT") private String requiredEventsCsv;
    @Column(name = "required_apis",            columnDefinition = "TEXT") private String requiredApisCsv;
    @Column(name = "required_data_categories", columnDefinition = "TEXT") private String requiredDataCategoriesCsv;
    @Column(name = "required_permissions",     columnDefinition = "TEXT") private String requiredPermissionsCsv;
    @Column(name = "facility_type_whitelist",  columnDefinition = "TEXT") private String facilityTypeWhitelistCsv;
    @Column(name = "roles_allowed",            columnDefinition = "TEXT") private String rolesAllowedCsv;
    @Column(name = "consent_requirements",     columnDefinition = "TEXT") private String consentRequirementsCsv;
    @Column(name = "health_status", nullable = false) private String healthStatus = "HEALTHY";
    @Column(name = "last_updated",  nullable = false) private OffsetDateTime lastUpdated = OffsetDateTime.now();
    @Column(name = "deprecation_date") private OffsetDateTime deprecationDate;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate void touch() { this.updatedAt = OffsetDateTime.now(); }

    public String getId() { return id; } public void setId(String s) { this.id = s; }
    public String getItemCode() { return itemCode; } public void setItemCode(String s) { this.itemCode = s; }
    public String getName() { return name; } public void setName(String s) { this.name = s; }
    public String getDescription() { return description; } public void setDescription(String s) { this.description = s; }
    public String getType() { return type; } public void setType(String s) { this.type = s; }
    public String getCategory() { return category; } public void setCategory(String s) { this.category = s; }
    public String getPublisherId() { return publisherId; } public void setPublisherId(String s) { this.publisherId = s; }
    public String getVersion() { return version; } public void setVersion(String s) { this.version = s; }
    public String getCompatibilityVersion() { return compatibilityVersion; } public void setCompatibilityVersion(String s) { this.compatibilityVersion = s; }
    public String getSupportedEnvironments() { return supportedEnvironments; } public void setSupportedEnvironments(String s) { this.supportedEnvironments = s; }
    public String getVisibility() { return visibility; } public void setVisibility(String s) { this.visibility = s; }
    public String getIconRef() { return iconRef; } public void setIconRef(String s) { this.iconRef = s; }
    public String getDocumentationUrl() { return documentationUrl; } public void setDocumentationUrl(String s) { this.documentationUrl = s; }
    public String getManifestRef() { return manifestRef; } public void setManifestRef(String s) { this.manifestRef = s; }
    public String getSecurityClassification() { return securityClassification; } public void setSecurityClassification(String s) { this.securityClassification = s; }
    public String getDataProtectionClassification() { return dataProtectionClassification; } public void setDataProtectionClassification(String s) { this.dataProtectionClassification = s; }
    public String getClinicalSafetyClassification() { return clinicalSafetyClassification; } public void setClinicalSafetyClassification(String s) { this.clinicalSafetyClassification = s; }
    public String getApprovalStatus() { return approvalStatus; } public void setApprovalStatus(String s) { this.approvalStatus = s; }
    public String getCertificationStatus() { return certificationStatus; } public void setCertificationStatus(String s) { this.certificationStatus = s; }
    public String getRegulatoryStatus() { return regulatoryStatus; } public void setRegulatoryStatus(String s) { this.regulatoryStatus = s; }
    public String getPricingModel() { return pricingModel; } public void setPricingModel(String s) { this.pricingModel = s; }
    public String getLicensingModel() { return licensingModel; } public void setLicensingModel(String s) { this.licensingModel = s; }
    public String getRequiredScopesCsv() { return requiredScopesCsv; } public void setRequiredScopesCsv(String s) { this.requiredScopesCsv = s; }
    public String getRequiredEventsCsv() { return requiredEventsCsv; } public void setRequiredEventsCsv(String s) { this.requiredEventsCsv = s; }
    public String getRequiredApisCsv() { return requiredApisCsv; } public void setRequiredApisCsv(String s) { this.requiredApisCsv = s; }
    public String getRequiredDataCategoriesCsv() { return requiredDataCategoriesCsv; } public void setRequiredDataCategoriesCsv(String s) { this.requiredDataCategoriesCsv = s; }
    public String getRequiredPermissionsCsv() { return requiredPermissionsCsv; } public void setRequiredPermissionsCsv(String s) { this.requiredPermissionsCsv = s; }
    public String getFacilityTypeWhitelistCsv() { return facilityTypeWhitelistCsv; } public void setFacilityTypeWhitelistCsv(String s) { this.facilityTypeWhitelistCsv = s; }
    public String getRolesAllowedCsv() { return rolesAllowedCsv; } public void setRolesAllowedCsv(String s) { this.rolesAllowedCsv = s; }
    public String getConsentRequirementsCsv() { return consentRequirementsCsv; } public void setConsentRequirementsCsv(String s) { this.consentRequirementsCsv = s; }
    public String getHealthStatus() { return healthStatus; } public void setHealthStatus(String s) { this.healthStatus = s; }
    public OffsetDateTime getLastUpdated() { return lastUpdated; } public void setLastUpdated(OffsetDateTime t) { this.lastUpdated = t; }
    public OffsetDateTime getDeprecationDate() { return deprecationDate; } public void setDeprecationDate(OffsetDateTime t) { this.deprecationDate = t; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime t) { this.createdAt = t; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(OffsetDateTime t) { this.updatedAt = t; }
}
