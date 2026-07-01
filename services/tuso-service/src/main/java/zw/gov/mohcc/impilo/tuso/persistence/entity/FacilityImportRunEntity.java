package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Audit record of a single facility master-pack import run (maps {@code tuso.facility_import_run},
 * created in {@code V011}). One row per {@code importPack} invocation — dry-run or real — capturing
 * the outcome counts and the quality summary so operators can review batches over time.
 */
@Entity
@Table(name = "facility_import_run", schema = "tuso")
public class FacilityImportRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pack_id", nullable = false, length = 64)
    private String packId;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun;

    @Column(name = "records_total", nullable = false)
    private int recordsTotal;

    @Column(name = "records_created", nullable = false)
    private int recordsCreated;

    @Column(name = "records_updated", nullable = false)
    private int recordsUpdated;

    @Column(name = "records_skipped", nullable = false)
    private int recordsSkipped;

    @Column(name = "records_failed", nullable = false)
    private int recordsFailed;

    @Column(name = "warnings_count", nullable = false)
    private int warningsCount;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "COMPLETED";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quality_report", columnDefinition = "jsonb")
    private Map<String, Object> qualityReport;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "initiated_by", length = 255)
    private String initiatedBy;

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getPackId() { return packId; }
    public void setPackId(String packId) { this.packId = packId; }

    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }

    public int getRecordsTotal() { return recordsTotal; }
    public void setRecordsTotal(int recordsTotal) { this.recordsTotal = recordsTotal; }

    public int getRecordsCreated() { return recordsCreated; }
    public void setRecordsCreated(int recordsCreated) { this.recordsCreated = recordsCreated; }

    public int getRecordsUpdated() { return recordsUpdated; }
    public void setRecordsUpdated(int recordsUpdated) { this.recordsUpdated = recordsUpdated; }

    public int getRecordsSkipped() { return recordsSkipped; }
    public void setRecordsSkipped(int recordsSkipped) { this.recordsSkipped = recordsSkipped; }

    public int getRecordsFailed() { return recordsFailed; }
    public void setRecordsFailed(int recordsFailed) { this.recordsFailed = recordsFailed; }

    public int getWarningsCount() { return warningsCount; }
    public void setWarningsCount(int warningsCount) { this.warningsCount = warningsCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Map<String, Object> getQualityReport() { return qualityReport; }
    public void setQualityReport(Map<String, Object> qualityReport) { this.qualityReport = qualityReport; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public String getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }
}
