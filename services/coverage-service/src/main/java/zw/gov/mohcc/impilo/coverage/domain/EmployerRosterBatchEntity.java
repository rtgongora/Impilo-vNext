package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Employer roster batch — stage -> validate -> apply (spec §24). */
@Entity
@Table(name = "cv_employer_roster_batches")
public class EmployerRosterBatchEntity {

    @Id @Column(name = "id", nullable = false) private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "pod_id", nullable = false, length = 64) private String podId = "national-spine";
    @Column(name = "employer_id", nullable = false) private UUID employerId;
    @Column(name = "plan_id", nullable = false) private UUID planId;
    @Column(name = "status", nullable = false, length = 16) private String status = "STAGED";
    @Column(name = "total_rows", nullable = false) private int totalRows = 0;
    @Column(name = "valid_rows", nullable = false) private int validRows = 0;
    @Column(name = "invalid_rows", nullable = false) private int invalidRows = 0;
    @Column(name = "applied_rows", nullable = false) private int appliedRows = 0;
    @Column(name = "uploaded_by", length = 128) private String uploadedBy;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt = OffsetDateTime.now();

    public EmployerRosterBatchEntity() {}

    public static EmployerRosterBatchEntity create(UUID tenantId, String podId, UUID employerId, UUID planId, String uploadedBy) {
        EmployerRosterBatchEntity b = new EmployerRosterBatchEntity();
        b.id = UUID.randomUUID();
        b.tenantId = tenantId;
        b.podId = podId != null ? podId : "national-spine";
        b.employerId = employerId;
        b.planId = planId;
        b.uploadedBy = uploadedBy;
        return b;
    }

    public void touch() { this.updatedAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getPodId() { return podId; }
    public UUID getEmployerId() { return employerId; }
    public UUID getPlanId() { return planId; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int v) { this.totalRows = v; }
    public int getValidRows() { return validRows; }
    public void setValidRows(int v) { this.validRows = v; }
    public int getInvalidRows() { return invalidRows; }
    public void setInvalidRows(int v) { this.invalidRows = v; }
    public int getAppliedRows() { return appliedRows; }
    public void setAppliedRows(int v) { this.appliedRows = v; }
    public String getUploadedBy() { return uploadedBy; }
}
