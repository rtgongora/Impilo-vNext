package zw.gov.mohcc.impilo.msika.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "msika_import_jobs")
public class ImportJobEntity {

    @Id
    @Column(name = "job_id", length = 26)
    private String jobId;

    @Column(name = "source_id", length = 26)
    private String sourceId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "catalog_id", length = 26)
    private String catalogId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "total_rows")
    private int totalRows;

    @Column(name = "valid_rows")
    private int validRows;

    @Column(name = "duplicate_rows")
    private int duplicateRows;

    @Column(name = "error_rows")
    private int errorRows;

    @Column(name = "imported_rows")
    private int importedRows;

    @Column(name = "stats", columnDefinition = "jsonb")
    private String stats;

    @Column(name = "error_report_doc_id", length = 255)
    private String errorReportDocId;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        if (status == null) status = "PENDING";
    }

    public String getId() { return jobId; }
    public void setId(String id) { this.jobId = id; }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getCatalogId() { return catalogId; }
    public void setCatalogId(String catalogId) { this.catalogId = catalogId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }
    public int getValidRows() { return validRows; }
    public void setValidRows(int validRows) { this.validRows = validRows; }
    public int getDuplicateRows() { return duplicateRows; }
    public void setDuplicateRows(int duplicateRows) { this.duplicateRows = duplicateRows; }
    public int getErrorRows() { return errorRows; }
    public void setErrorRows(int errorRows) { this.errorRows = errorRows; }
    public int getImportedRows() { return importedRows; }
    public void setImportedRows(int importedRows) { this.importedRows = importedRows; }
    public String getStats() { return stats; }
    public void setStats(String stats) { this.stats = stats; }
    public String getErrorReportDocId() { return errorReportDocId; }
    public void setErrorReportDocId(String errorReportDocId) { this.errorReportDocId = errorReportDocId; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
}
