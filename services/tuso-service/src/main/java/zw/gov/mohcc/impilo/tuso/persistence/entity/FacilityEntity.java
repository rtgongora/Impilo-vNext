package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "facility", schema = "tuso")
public class FacilityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Canonical facility UUID (facility-lifecycle wave, Option 1). Stable, service-neutral facility
     * identity that downstream services (PCT queues/care locations, Dura stores, Ndila map points,
     * Vashandi locations) key off, instead of correlating TUSO's numeric id to per-service UUIDs.
     */
    @Column(name = "facility_uuid", unique = true, updatable = false)
    private UUID facilityUuid;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_code", nullable = false, length = 50)
    private String facilityCode;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "facility_type", length = 50)
    private String facilityType;

    @Column(name = "province", length = 100)
    private String province;

    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "status", length = 20)
    private String status = "ACTIVE";

    @Column(name = "gofr_id", length = 255)
    private String gofrId;

    @Column(name = "ownership", length = 50)
    private String ownership;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "alias_names", columnDefinition = "jsonb")
    private List<String> aliasNames;

    @Column(name = "operating_entity", length = 255)
    private String operatingEntity;

    @Column(name = "facility_class", length = 100)
    private String facilityClass;

    @Column(name = "facility_category", length = 100)
    private String facilityCategory;

    @Column(name = "legal_status", length = 100)
    private String legalStatus;

    @Column(name = "registration_pathway", length = 64)
    private String registrationPathway;

    @Column(name = "institution_file_number", length = 64)
    private String institutionFileNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "regulatory_status", length = 64)
    private FacilityRegulatoryStatus regulatoryStatus = FacilityRegulatoryStatus.DRAFT;

    @Column(name = "regulatory_status_updated_at")
    private Instant regulatoryStatusUpdatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "level", length = 50)
    private String level;

    @Column(name = "facility_tier", length = 64)
    private String facilityTier;

    @Column(name = "deployment_mode", length = 32)
    private String deploymentMode;

    @Column(name = "continuity_class", length = 32)
    private String continuityClass;

    @Column(name = "workflow_archetype", length = 64)
    private String workflowArchetype;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private FacilityEntity parent;

    @Column(name = "operational_status", length = 30)
    private String operationalStatus = "OPERATIONAL";

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "opened_date")
    private LocalDate openedDate;

    @Column(name = "closed_date")
    private LocalDate closedDate;

    @Column(name = "close_reason", columnDefinition = "TEXT")
    private String closeReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merged_into_id")
    private FacilityEntity mergedInto;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (facilityUuid == null) {
            facilityUuid = UUID.randomUUID();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getFacilityUuid() { return facilityUuid; }
    public void setFacilityUuid(UUID facilityUuid) { this.facilityUuid = facilityUuid; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getFacilityCode() { return facilityCode; }
    public void setFacilityCode(String facilityCode) { this.facilityCode = facilityCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFacilityType() { return facilityType; }
    public void setFacilityType(String facilityType) { this.facilityType = facilityType; }

    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }

    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getGofrId() { return gofrId; }
    public void setGofrId(String gofrId) { this.gofrId = gofrId; }

    public String getOwnership() { return ownership; }
    public void setOwnership(String ownership) { this.ownership = ownership; }

    public List<String> getAliasNames() { return aliasNames; }
    public void setAliasNames(List<String> aliasNames) { this.aliasNames = aliasNames; }

    public String getOperatingEntity() { return operatingEntity; }
    public void setOperatingEntity(String operatingEntity) { this.operatingEntity = operatingEntity; }

    public String getFacilityClass() { return facilityClass; }
    public void setFacilityClass(String facilityClass) { this.facilityClass = facilityClass; }

    public String getFacilityCategory() { return facilityCategory; }
    public void setFacilityCategory(String facilityCategory) { this.facilityCategory = facilityCategory; }

    public String getLegalStatus() { return legalStatus; }
    public void setLegalStatus(String legalStatus) { this.legalStatus = legalStatus; }

    public String getRegistrationPathway() { return registrationPathway; }
    public void setRegistrationPathway(String registrationPathway) { this.registrationPathway = registrationPathway; }

    public String getInstitutionFileNumber() { return institutionFileNumber; }
    public void setInstitutionFileNumber(String institutionFileNumber) { this.institutionFileNumber = institutionFileNumber; }

    public FacilityRegulatoryStatus getRegulatoryStatus() { return regulatoryStatus; }
    public void setRegulatoryStatus(FacilityRegulatoryStatus regulatoryStatus) { this.regulatoryStatus = regulatoryStatus; }

    public Instant getRegulatoryStatusUpdatedAt() { return regulatoryStatusUpdatedAt; }
    public void setRegulatoryStatusUpdatedAt(Instant regulatoryStatusUpdatedAt) { this.regulatoryStatusUpdatedAt = regulatoryStatusUpdatedAt; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getFacilityTier() { return facilityTier; }
    public void setFacilityTier(String facilityTier) { this.facilityTier = facilityTier; }

    public String getDeploymentMode() { return deploymentMode; }
    public void setDeploymentMode(String deploymentMode) { this.deploymentMode = deploymentMode; }

    public String getContinuityClass() { return continuityClass; }
    public void setContinuityClass(String continuityClass) { this.continuityClass = continuityClass; }

    public String getWorkflowArchetype() { return workflowArchetype; }
    public void setWorkflowArchetype(String workflowArchetype) { this.workflowArchetype = workflowArchetype; }

    public FacilityEntity getParent() { return parent; }
    public void setParent(FacilityEntity parent) { this.parent = parent; }

    public String getOperationalStatus() { return operationalStatus; }
    public void setOperationalStatus(String operationalStatus) { this.operationalStatus = operationalStatus; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDate getOpenedDate() { return openedDate; }
    public void setOpenedDate(LocalDate openedDate) { this.openedDate = openedDate; }

    public LocalDate getClosedDate() { return closedDate; }
    public void setClosedDate(LocalDate closedDate) { this.closedDate = closedDate; }

    public String getCloseReason() { return closeReason; }
    public void setCloseReason(String closeReason) { this.closeReason = closeReason; }

    public FacilityEntity getMergedInto() { return mergedInto; }
    public void setMergedInto(FacilityEntity mergedInto) { this.mergedInto = mergedInto; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
