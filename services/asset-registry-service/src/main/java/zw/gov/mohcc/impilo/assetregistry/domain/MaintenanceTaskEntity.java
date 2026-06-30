package zw.gov.mohcc.impilo.assetregistry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Preventive / corrective / inspection maintenance task. Spare parts and technicians
 *  are REFERENCED (inventory-service/Oros, Vashandi) — never owned here. */
@Entity
@Table(name = "asr_maintenance_task")
public class MaintenanceTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "task_type", nullable = false, length = 24)
    private String taskType;

    @Column(name = "title", nullable = false, length = 512)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "priority", nullable = false, length = 16)
    private String priority = "NORMAL";

    @Column(name = "status", nullable = false, length = 24)
    private String status = "OPEN";

    @Column(name = "technician_ref", length = 255)
    private String technicianRef;

    @Column(name = "spare_part_request_ref", length = 255)
    private String sparePartRequestRef;

    @Column(name = "fault_report_id")
    private UUID faultReportId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "downtime_minutes")
    private Integer downtimeMinutes;

    @Column(name = "service_report", columnDefinition = "text")
    private String serviceReport;

    @Column(name = "opened_by", length = 255)
    private String openedBy;

    @Column(name = "completed_by", length = 255)
    private String completedBy;

    @Column(name = "opened_at", nullable = false)
    private OffsetDateTime openedAt = OffsetDateTime.now();

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private String metadataJson;

    protected MaintenanceTaskEntity() {}

    public MaintenanceTaskEntity(UUID tenantId, UUID equipmentId, String taskType, String title) {
        this.tenantId = tenantId;
        this.equipmentId = equipmentId;
        this.taskType = taskType;
        this.title = title;
        this.openedAt = OffsetDateTime.now();
    }

    public UUID getTaskId() { return taskId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getEquipmentId() { return equipmentId; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTechnicianRef() { return technicianRef; }
    public void setTechnicianRef(String technicianRef) { this.technicianRef = technicianRef; }
    public String getSparePartRequestRef() { return sparePartRequestRef; }
    public void setSparePartRequestRef(String sparePartRequestRef) { this.sparePartRequestRef = sparePartRequestRef; }
    public UUID getFaultReportId() { return faultReportId; }
    public void setFaultReportId(UUID faultReportId) { this.faultReportId = faultReportId; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public Integer getDowntimeMinutes() { return downtimeMinutes; }
    public void setDowntimeMinutes(Integer downtimeMinutes) { this.downtimeMinutes = downtimeMinutes; }
    public String getServiceReport() { return serviceReport; }
    public void setServiceReport(String serviceReport) { this.serviceReport = serviceReport; }
    public String getOpenedBy() { return openedBy; }
    public void setOpenedBy(String openedBy) { this.openedBy = openedBy; }
    public String getCompletedBy() { return completedBy; }
    public void setCompletedBy(String completedBy) { this.completedBy = completedBy; }
    public OffsetDateTime getOpenedAt() { return openedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}
