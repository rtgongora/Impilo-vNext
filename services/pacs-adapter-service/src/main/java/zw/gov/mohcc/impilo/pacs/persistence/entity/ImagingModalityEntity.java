package zw.gov.mohcc.impilo.pacs.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Facility modality / machine registry row ({@code pacs.imaging_modality}).
 * Represents a physical acquisition device (DX, CR, CT, MR, US, MG, XA, …) with its
 * DICOM networking configuration for real ground deployment. Network fields are
 * technical-role data — the BFF redacts them for non-technical actors.
 */
@Entity
@Table(name = "imaging_modality", schema = "pacs")
public class ImagingModalityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false, length = 128)
    private String facilityId;

    @Column(name = "modality_type", nullable = false, length = 20)
    private String modalityType;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "ae_title", length = 64)
    private String aeTitle;

    @Column(name = "host", length = 255)
    private String host;

    @Column(name = "port")
    private Integer port;

    @Column(name = "called_ae_title", length = 64)
    private String calledAeTitle;

    @Column(name = "calling_ae_title", length = 64)
    private String callingAeTitle;

    @Column(name = "vendor", length = 255)
    private String vendor;

    @Column(name = "model", length = 255)
    private String model;

    @Column(name = "serial_number", length = 128)
    private String serialNumber;

    @Column(name = "supports_dicom_storage", nullable = false)
    private boolean supportsDicomStorage;

    @Column(name = "supports_worklist", nullable = false)
    private boolean supportsWorklist;

    @Column(name = "supports_mpps", nullable = false)
    private boolean supportsMpps;

    @Column(name = "supports_dicomweb", nullable = false)
    private boolean supportsDicomweb;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "export_options", columnDefinition = "jsonb")
    private Map<String, Object> exportOptions;

    @Column(name = "operational_status", nullable = false, length = 30)
    private String operationalStatus = "UNKNOWN";

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    @Column(name = "default_storage_destination", length = 255)
    private String defaultStorageDestination;

    @Column(name = "default_worklist_source", length = 255)
    private String defaultWorklistSource;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "active", nullable = false)
    private boolean active = true;

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

    public String getModalityType() { return modalityType; }
    public void setModalityType(String modalityType) { this.modalityType = modalityType; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAeTitle() { return aeTitle; }
    public void setAeTitle(String aeTitle) { this.aeTitle = aeTitle; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }

    public String getCalledAeTitle() { return calledAeTitle; }
    public void setCalledAeTitle(String calledAeTitle) { this.calledAeTitle = calledAeTitle; }

    public String getCallingAeTitle() { return callingAeTitle; }
    public void setCallingAeTitle(String callingAeTitle) { this.callingAeTitle = callingAeTitle; }

    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public boolean isSupportsDicomStorage() { return supportsDicomStorage; }
    public void setSupportsDicomStorage(boolean supportsDicomStorage) { this.supportsDicomStorage = supportsDicomStorage; }

    public boolean isSupportsWorklist() { return supportsWorklist; }
    public void setSupportsWorklist(boolean supportsWorklist) { this.supportsWorklist = supportsWorklist; }

    public boolean isSupportsMpps() { return supportsMpps; }
    public void setSupportsMpps(boolean supportsMpps) { this.supportsMpps = supportsMpps; }

    public boolean isSupportsDicomweb() { return supportsDicomweb; }
    public void setSupportsDicomweb(boolean supportsDicomweb) { this.supportsDicomweb = supportsDicomweb; }

    public Map<String, Object> getExportOptions() { return exportOptions; }
    public void setExportOptions(Map<String, Object> exportOptions) { this.exportOptions = exportOptions; }

    public String getOperationalStatus() { return operationalStatus; }
    public void setOperationalStatus(String operationalStatus) { this.operationalStatus = operationalStatus; }

    public OffsetDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(OffsetDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }

    public String getDefaultStorageDestination() { return defaultStorageDestination; }
    public void setDefaultStorageDestination(String defaultStorageDestination) { this.defaultStorageDestination = defaultStorageDestination; }

    public String getDefaultWorklistSource() { return defaultWorklistSource; }
    public void setDefaultWorklistSource(String defaultWorklistSource) { this.defaultWorklistSource = defaultWorklistSource; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
