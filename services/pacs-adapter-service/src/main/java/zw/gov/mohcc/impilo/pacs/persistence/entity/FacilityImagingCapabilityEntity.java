package zw.gov.mohcc.impilo.pacs.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Per-facility imaging capability / deployment-mode record ({@code pacs.facility_imaging_capability}).
 * One row per (tenant, facility). Facility identity itself remains owned by TUSO — this table
 * only records the facility's imaging-integration truth.
 */
@Entity
@Table(name = "facility_imaging_capability", schema = "pacs")
public class FacilityImagingCapabilityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false, length = 128)
    private String facilityId;

    @Column(name = "facility_name", length = 255)
    private String facilityName;

    @Column(name = "deployment_mode", nullable = false, length = 40)
    private String deploymentMode = "NONE";

    @Column(name = "pacs_vna_available", nullable = false)
    private boolean pacsVnaAvailable;

    @Column(name = "local_gateway_available", nullable = false)
    private boolean localGatewayAvailable;

    @Column(name = "dicomweb_available", nullable = false)
    private boolean dicomwebAvailable;

    @Column(name = "dimse_available", nullable = false)
    private boolean dimseAvailable;

    @Column(name = "mwl_supported", nullable = false)
    private boolean mwlSupported;

    @Column(name = "cstore_receive_supported", nullable = false)
    private boolean cstoreReceiveSupported;

    @Column(name = "mpps_supported", nullable = false)
    private boolean mppsSupported;

    @Column(name = "store_forward_supported", nullable = false)
    private boolean storeForwardSupported;

    @Column(name = "local_cache_supported", nullable = false)
    private boolean localCacheSupported;

    @Column(name = "manual_import_supported", nullable = false)
    private boolean manualImportSupported;

    @Column(name = "digitisation_bridge_supported", nullable = false)
    private boolean digitisationBridgeSupported;

    @Column(name = "viewer_available", nullable = false)
    private boolean viewerAvailable;

    @Column(name = "reporting_available", nullable = false)
    private boolean reportingAvailable;

    @Column(name = "internet_quality", length = 30)
    private String internetQuality;

    @Column(name = "operational_status", nullable = false, length = 30)
    private String operationalStatus = "UNKNOWN";

    @Column(name = "default_archive_target", length = 255)
    private String defaultArchiveTarget;

    @Column(name = "default_worklist_target", length = 255)
    private String defaultWorklistTarget;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "supported_modalities", columnDefinition = "jsonb")
    private List<String> supportedModalities;

    @Column(name = "configuration_status", nullable = false, length = 30)
    private String configurationStatus = "UNCONFIGURED";

    @Column(name = "go_live_readiness", nullable = false, length = 30)
    private String goLiveReadiness = "NOT_READY";

    @Column(name = "last_heartbeat_at")
    private OffsetDateTime lastHeartbeatAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "updated_by", columnDefinition = "TEXT")
    private String updatedBy;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getFacilityId() { return facilityId; }
    public void setFacilityId(String facilityId) { this.facilityId = facilityId; }

    public String getFacilityName() { return facilityName; }
    public void setFacilityName(String facilityName) { this.facilityName = facilityName; }

    public String getDeploymentMode() { return deploymentMode; }
    public void setDeploymentMode(String deploymentMode) { this.deploymentMode = deploymentMode; }

    public boolean isPacsVnaAvailable() { return pacsVnaAvailable; }
    public void setPacsVnaAvailable(boolean pacsVnaAvailable) { this.pacsVnaAvailable = pacsVnaAvailable; }

    public boolean isLocalGatewayAvailable() { return localGatewayAvailable; }
    public void setLocalGatewayAvailable(boolean localGatewayAvailable) { this.localGatewayAvailable = localGatewayAvailable; }

    public boolean isDicomwebAvailable() { return dicomwebAvailable; }
    public void setDicomwebAvailable(boolean dicomwebAvailable) { this.dicomwebAvailable = dicomwebAvailable; }

    public boolean isDimseAvailable() { return dimseAvailable; }
    public void setDimseAvailable(boolean dimseAvailable) { this.dimseAvailable = dimseAvailable; }

    public boolean isMwlSupported() { return mwlSupported; }
    public void setMwlSupported(boolean mwlSupported) { this.mwlSupported = mwlSupported; }

    public boolean isCstoreReceiveSupported() { return cstoreReceiveSupported; }
    public void setCstoreReceiveSupported(boolean cstoreReceiveSupported) { this.cstoreReceiveSupported = cstoreReceiveSupported; }

    public boolean isMppsSupported() { return mppsSupported; }
    public void setMppsSupported(boolean mppsSupported) { this.mppsSupported = mppsSupported; }

    public boolean isStoreForwardSupported() { return storeForwardSupported; }
    public void setStoreForwardSupported(boolean storeForwardSupported) { this.storeForwardSupported = storeForwardSupported; }

    public boolean isLocalCacheSupported() { return localCacheSupported; }
    public void setLocalCacheSupported(boolean localCacheSupported) { this.localCacheSupported = localCacheSupported; }

    public boolean isManualImportSupported() { return manualImportSupported; }
    public void setManualImportSupported(boolean manualImportSupported) { this.manualImportSupported = manualImportSupported; }

    public boolean isDigitisationBridgeSupported() { return digitisationBridgeSupported; }
    public void setDigitisationBridgeSupported(boolean digitisationBridgeSupported) { this.digitisationBridgeSupported = digitisationBridgeSupported; }

    public boolean isViewerAvailable() { return viewerAvailable; }
    public void setViewerAvailable(boolean viewerAvailable) { this.viewerAvailable = viewerAvailable; }

    public boolean isReportingAvailable() { return reportingAvailable; }
    public void setReportingAvailable(boolean reportingAvailable) { this.reportingAvailable = reportingAvailable; }

    public String getInternetQuality() { return internetQuality; }
    public void setInternetQuality(String internetQuality) { this.internetQuality = internetQuality; }

    public String getOperationalStatus() { return operationalStatus; }
    public void setOperationalStatus(String operationalStatus) { this.operationalStatus = operationalStatus; }

    public String getDefaultArchiveTarget() { return defaultArchiveTarget; }
    public void setDefaultArchiveTarget(String defaultArchiveTarget) { this.defaultArchiveTarget = defaultArchiveTarget; }

    public String getDefaultWorklistTarget() { return defaultWorklistTarget; }
    public void setDefaultWorklistTarget(String defaultWorklistTarget) { this.defaultWorklistTarget = defaultWorklistTarget; }

    public List<String> getSupportedModalities() { return supportedModalities; }
    public void setSupportedModalities(List<String> supportedModalities) { this.supportedModalities = supportedModalities; }

    public String getConfigurationStatus() { return configurationStatus; }
    public void setConfigurationStatus(String configurationStatus) { this.configurationStatus = configurationStatus; }

    public String getGoLiveReadiness() { return goLiveReadiness; }
    public void setGoLiveReadiness(String goLiveReadiness) { this.goLiveReadiness = goLiveReadiness; }

    public OffsetDateTime getLastHeartbeatAt() { return lastHeartbeatAt; }
    public void setLastHeartbeatAt(OffsetDateTime lastHeartbeatAt) { this.lastHeartbeatAt = lastHeartbeatAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
