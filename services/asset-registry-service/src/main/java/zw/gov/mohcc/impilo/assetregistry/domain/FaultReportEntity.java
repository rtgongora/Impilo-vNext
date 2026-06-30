package zw.gov.mohcc.impilo.assetregistry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Fault report. SAFETY severity links to Rito (case ref); otherwise spawns a maintenance task. */
@Entity
@Table(name = "asr_fault_report")
public class FaultReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "fault_id")
    private UUID faultId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "severity", nullable = false, length = 16)
    private String severity = "MINOR";

    @Column(name = "summary", nullable = false, length = 512)
    private String summary;

    @Column(name = "detail", columnDefinition = "text")
    private String detail;

    @Column(name = "reported_by", length = 255)
    private String reportedBy;

    @Column(name = "safety_concern", nullable = false)
    private boolean safetyConcern = false;

    @Column(name = "rito_case_ref", length = 255)
    private String ritoCaseRef;

    @Column(name = "maintenance_task_id")
    private UUID maintenanceTaskId;

    @Column(name = "status", nullable = false, length = 24)
    private String status = "OPEN";

    @Column(name = "reported_at", nullable = false)
    private OffsetDateTime reportedAt = OffsetDateTime.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private String metadataJson;

    protected FaultReportEntity() {}

    public FaultReportEntity(UUID tenantId, UUID equipmentId, String summary) {
        this.tenantId = tenantId;
        this.equipmentId = equipmentId;
        this.summary = summary;
        this.reportedAt = OffsetDateTime.now();
    }

    public UUID getFaultId() { return faultId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getEquipmentId() { return equipmentId; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getReportedBy() { return reportedBy; }
    public void setReportedBy(String reportedBy) { this.reportedBy = reportedBy; }
    public boolean isSafetyConcern() { return safetyConcern; }
    public void setSafetyConcern(boolean safetyConcern) { this.safetyConcern = safetyConcern; }
    public String getRitoCaseRef() { return ritoCaseRef; }
    public void setRitoCaseRef(String ritoCaseRef) { this.ritoCaseRef = ritoCaseRef; }
    public UUID getMaintenanceTaskId() { return maintenanceTaskId; }
    public void setMaintenanceTaskId(UUID maintenanceTaskId) { this.maintenanceTaskId = maintenanceTaskId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getReportedAt() { return reportedAt; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}
