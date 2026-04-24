package zw.gov.mohcc.impilo.indawo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ind_sites")
public class SiteEntity {

    @Id
    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "type", nullable = false, length = 64)
    private String type;

    @Column(name = "address_json", columnDefinition = "JSONB")
    private String addressJson;

    @Column(name = "geo_json", columnDefinition = "JSONB")
    private String geoJson;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "site_code", length = 64)
    private String siteCode;

    @Column(name = "site_category", length = 64)
    private String siteCategory;

    @Column(name = "risk_class", length = 32)
    private String riskClass;

    @Column(name = "legal_status", length = 32)
    private String legalStatus;

    @Column(name = "ownership_type", length = 32)
    private String ownershipType;

    @Column(name = "ownership_name", length = 255)
    private String ownershipName;

    @Column(name = "operator_name", length = 255)
    private String operatorName;

    @Column(name = "registration_pathway", length = 64)
    private String registrationPathway;

    @Column(name = "province", length = 128)
    private String province;

    @Column(name = "district", length = 128)
    private String district;

    @Column(name = "ward", length = 128)
    private String ward;

    @Column(name = "jurisdiction_ref", length = 255)
    private String jurisdictionRef;

    @Column(name = "lifecycle_status", nullable = false, length = 48)
    private String lifecycleStatus = SiteLifecycleStatus.DRAFT.name();

    @Column(name = "regulatory_status", nullable = false, length = 48)
    private String regulatoryStatus = SiteRegulatoryStatus.APPLICATION_IN_PROGRESS.name();

    @Column(name = "operational_status", nullable = false, length = 48)
    private String operationalStatus = "UNKNOWN";

    @Column(name = "active_flag", nullable = false)
    private boolean activeFlag = true;

    @Column(name = "metadata", columnDefinition = "JSONB")
    private String metadata;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected SiteEntity() {}

    public SiteEntity(UUID siteId, UUID tenantId, String name, String type) {
        this.siteId = siteId;
        this.tenantId = tenantId;
        this.name = name;
        this.type = type;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getSiteId() { return siteId; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getAddressJson() { return addressJson; }
    public void setAddressJson(String addressJson) { this.addressJson = addressJson; }
    public String getGeoJson() { return geoJson; }
    public void setGeoJson(String geoJson) { this.geoJson = geoJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public String getSiteCategory() { return siteCategory; }
    public void setSiteCategory(String siteCategory) { this.siteCategory = siteCategory; }
    public String getRiskClass() { return riskClass; }
    public void setRiskClass(String riskClass) { this.riskClass = riskClass; }
    public String getLegalStatus() { return legalStatus; }
    public void setLegalStatus(String legalStatus) { this.legalStatus = legalStatus; }
    public String getOwnershipType() { return ownershipType; }
    public void setOwnershipType(String ownershipType) { this.ownershipType = ownershipType; }
    public String getOwnershipName() { return ownershipName; }
    public void setOwnershipName(String ownershipName) { this.ownershipName = ownershipName; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getRegistrationPathway() { return registrationPathway; }
    public void setRegistrationPathway(String registrationPathway) { this.registrationPathway = registrationPathway; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getWard() { return ward; }
    public void setWard(String ward) { this.ward = ward; }
    public String getJurisdictionRef() { return jurisdictionRef; }
    public void setJurisdictionRef(String jurisdictionRef) { this.jurisdictionRef = jurisdictionRef; }
    public String getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(String lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }
    public String getRegulatoryStatus() { return regulatoryStatus; }
    public void setRegulatoryStatus(String regulatoryStatus) { this.regulatoryStatus = regulatoryStatus; }
    public String getOperationalStatus() { return operationalStatus; }
    public void setOperationalStatus(String operationalStatus) { this.operationalStatus = operationalStatus; }
    public boolean isActiveFlag() { return activeFlag; }
    public void setActiveFlag(boolean activeFlag) { this.activeFlag = activeFlag; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
