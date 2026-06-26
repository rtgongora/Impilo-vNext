package zw.gov.mohcc.impilo.patientsafety.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Serious-AEFI investigation. Lifecycle:
 * NOT_REQUIRED → REQUIRED → PLANNED → IN_PROGRESS → INTERIM → FINAL → CLOSED.
 */
@Entity
@Table(name = "ps_aefi_investigation")
public class AefiInvestigationEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "case_id", nullable = false)
    private UUID caseId;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "investigation_reference", nullable = false, length = 32)
    private String investigationReference;
    @Column(name = "status", nullable = false, length = 16)
    private String status = "REQUIRED";
    @Column(name = "assigned_to", length = 255)
    private String assignedTo;
    @Column(name = "planned_date")
    private LocalDate plannedDate;
    @Column(name = "form_pack_key", length = 128)
    private String formPackKey;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_findings", columnDefinition = "jsonb")
    private String fieldFindings;
    @Column(name = "final_classification", length = 64)
    private String finalClassification;
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected AefiInvestigationEntity() {}

    public AefiInvestigationEntity(UUID caseId, UUID tenantId, String investigationReference) {
        this.id = UUID.randomUUID();
        this.caseId = caseId;
        this.tenantId = tenantId;
        this.investigationReference = investigationReference;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void touch() { this.updatedAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public UUID getCaseId() { return caseId; }
    public UUID getTenantId() { return tenantId; }
    public String getInvestigationReference() { return investigationReference; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String v) { this.assignedTo = v; }
    public LocalDate getPlannedDate() { return plannedDate; }
    public void setPlannedDate(LocalDate v) { this.plannedDate = v; }
    public String getFormPackKey() { return formPackKey; }
    public void setFormPackKey(String v) { this.formPackKey = v; }
    public String getFieldFindings() { return fieldFindings; }
    public void setFieldFindings(String v) { this.fieldFindings = v; }
    public String getFinalClassification() { return finalClassification; }
    public void setFinalClassification(String v) { this.finalClassification = v; }
    public String getSummary() { return summary; }
    public void setSummary(String v) { this.summary = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
