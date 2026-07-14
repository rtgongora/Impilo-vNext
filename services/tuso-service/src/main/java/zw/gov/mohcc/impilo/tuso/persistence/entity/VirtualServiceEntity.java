package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Virtual service-delivery entity (virtual hospital) — TUSO SoR.
 *
 * <p>Doctrine: virtual hospitals are service architecture, not building
 * architecture. This is a distinct identifier class (Virtual Hospital ID,
 * {@code vs_uid}) and must never be collapsed into {@code tuso.facility}.</p>
 *
 * <p>Lifecycle is fail-closed: {@code CONFIGURED} →
 * {@code ACTIVATION_REQUESTED} (preconditions held: ≥1 active routing seam AND
 * ≥1 queue definition) → {@code ACTIVE} (only once PCT confirms pool
 * materialisation) → {@code SUSPENDED}. Stored state never overstates runtime
 * reality.</p>
 */
@Entity
@Table(name = "virtual_service_entity", schema = "tuso")
public class VirtualServiceEntity {

    public static final String LIFECYCLE_CONFIGURED = "CONFIGURED";
    public static final String LIFECYCLE_ACTIVATION_REQUESTED = "ACTIVATION_REQUESTED";
    public static final String LIFECYCLE_ACTIVE = "ACTIVE";
    public static final String LIFECYCLE_SUSPENDED = "SUSPENDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vs_uid", nullable = false, unique = true, length = 64)
    private String vsUid;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "level", nullable = false, length = 32)
    private String level;

    @Column(name = "operating_model", nullable = false, length = 48)
    private String operatingModel;

    @Column(name = "operating_authority")
    private String operatingAuthority;

    @Column(name = "regulatory_status", length = 64)
    private String regulatoryStatus;

    @Column(name = "purpose", columnDefinition = "TEXT")
    private String purpose;

    @Column(name = "catchment")
    private String catchment;

    @Column(name = "province_code", length = 8)
    private String provinceCode;

    @Column(name = "cross_border_participation", nullable = false)
    private boolean crossBorderParticipation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_cross_border_privileges", columnDefinition = "jsonb")
    private String allowedCrossBorderPrivileges = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "billing_models", columnDefinition = "jsonb")
    private String billingModels = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "session_mode_ids", columnDefinition = "jsonb")
    private String sessionModeIds = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_pool_cadres", columnDefinition = "jsonb")
    private String providerPoolCadres = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "linked_facility_roles", columnDefinition = "jsonb")
    private String linkedFacilityRoles = "[]";

    @Column(name = "lifecycle_status", nullable = false, length = 32)
    private String lifecycleStatus = LIFECYCLE_CONFIGURED;

    @Column(name = "activated_by")
    private String activatedBy;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "virtualService", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    private List<VirtualServiceLineEntity> serviceLines = new ArrayList<>();

    @OneToMany(mappedBy = "virtualService", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<VirtualServiceRoutingSeamEntity> routingSeams = new ArrayList<>();

    @OneToMany(mappedBy = "virtualService", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<VirtualServiceQueueDefEntity> queueDefs = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getVsUid() { return vsUid; }
    public void setVsUid(String vsUid) { this.vsUid = vsUid; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getOperatingModel() { return operatingModel; }
    public void setOperatingModel(String operatingModel) { this.operatingModel = operatingModel; }
    public String getOperatingAuthority() { return operatingAuthority; }
    public void setOperatingAuthority(String operatingAuthority) { this.operatingAuthority = operatingAuthority; }
    public String getRegulatoryStatus() { return regulatoryStatus; }
    public void setRegulatoryStatus(String regulatoryStatus) { this.regulatoryStatus = regulatoryStatus; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getCatchment() { return catchment; }
    public void setCatchment(String catchment) { this.catchment = catchment; }
    public String getProvinceCode() { return provinceCode; }
    public void setProvinceCode(String provinceCode) { this.provinceCode = provinceCode; }
    public boolean isCrossBorderParticipation() { return crossBorderParticipation; }
    public void setCrossBorderParticipation(boolean crossBorderParticipation) { this.crossBorderParticipation = crossBorderParticipation; }
    public String getAllowedCrossBorderPrivileges() { return allowedCrossBorderPrivileges; }
    public void setAllowedCrossBorderPrivileges(String allowedCrossBorderPrivileges) { this.allowedCrossBorderPrivileges = allowedCrossBorderPrivileges; }
    public String getBillingModels() { return billingModels; }
    public void setBillingModels(String billingModels) { this.billingModels = billingModels; }
    public String getSessionModeIds() { return sessionModeIds; }
    public void setSessionModeIds(String sessionModeIds) { this.sessionModeIds = sessionModeIds; }
    public String getProviderPoolCadres() { return providerPoolCadres; }
    public void setProviderPoolCadres(String providerPoolCadres) { this.providerPoolCadres = providerPoolCadres; }
    public String getLinkedFacilityRoles() { return linkedFacilityRoles; }
    public void setLinkedFacilityRoles(String linkedFacilityRoles) { this.linkedFacilityRoles = linkedFacilityRoles; }
    public String getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(String lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }
    public String getActivatedBy() { return activatedBy; }
    public void setActivatedBy(String activatedBy) { this.activatedBy = activatedBy; }
    public OffsetDateTime getActivatedAt() { return activatedAt; }
    public void setActivatedAt(OffsetDateTime activatedAt) { this.activatedAt = activatedAt; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public List<VirtualServiceLineEntity> getServiceLines() { return serviceLines; }
    public List<VirtualServiceRoutingSeamEntity> getRoutingSeams() { return routingSeams; }
    public List<VirtualServiceQueueDefEntity> getQueueDefs() { return queueDefs; }
}
