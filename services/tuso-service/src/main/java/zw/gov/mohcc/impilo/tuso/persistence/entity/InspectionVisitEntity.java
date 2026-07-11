package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "inspection_visit", schema = "tuso")
public class InspectionVisitEntity {

    @Id
    @Column(name = "visit_id", nullable = false, updatable = false)
    private UUID visitId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "inspection_id", nullable = false)
    private UUID inspectionId;

    @Column(name = "visit_number", nullable = false)
    private Integer visitNumber = 1;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "actual_date")
    private LocalDate actualDate;

    @Column(name = "mode", nullable = false, length = 32)
    private String mode = "ONSITE";

    @Column(name = "lead_inspector_id", length = 255)
    private String leadInspectorId;

    @Column(name = "lead_inspector_name", length = 255)
    private String leadInspectorName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "team", columnDefinition = "jsonb")
    private List<Map<String, Object>> team;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "PLANNED";

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (visitId == null) {
            visitId = UUID.randomUUID();
        }
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getVisitId() { return visitId; }
    public void setVisitId(UUID visitId) { this.visitId = visitId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getInspectionId() { return inspectionId; }
    public void setInspectionId(UUID inspectionId) { this.inspectionId = inspectionId; }
    public Integer getVisitNumber() { return visitNumber; }
    public void setVisitNumber(Integer visitNumber) { this.visitNumber = visitNumber; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }
    public LocalDate getActualDate() { return actualDate; }
    public void setActualDate(LocalDate actualDate) { this.actualDate = actualDate; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getLeadInspectorId() { return leadInspectorId; }
    public void setLeadInspectorId(String leadInspectorId) { this.leadInspectorId = leadInspectorId; }
    public String getLeadInspectorName() { return leadInspectorName; }
    public void setLeadInspectorName(String leadInspectorName) { this.leadInspectorName = leadInspectorName; }
    public List<Map<String, Object>> getTeam() { return team; }
    public void setTeam(List<Map<String, Object>> team) { this.team = team; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
