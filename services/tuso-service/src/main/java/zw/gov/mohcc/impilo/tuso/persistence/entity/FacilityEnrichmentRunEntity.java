package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One classification-enrichment backfill run. Mirrors {@code facility_import_run}
 * (V011): records verified estate totals (never assumed), per-status counts, and
 * an exception report (catalogue gaps, conflicts, unparseable bed values).
 */
@Entity
@Table(name = "facility_enrichment_run", schema = "tuso")
public class FacilityEnrichmentRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun = true;

    @Column(name = "records_total", nullable = false)
    private Integer recordsTotal = 0;

    @Column(name = "records_evaluated", nullable = false)
    private Integer recordsEvaluated = 0;

    @Column(name = "records_created", nullable = false)
    private Integer recordsCreated = 0;

    @Column(name = "records_updated", nullable = false)
    private Integer recordsUpdated = 0;

    @Column(name = "records_unchanged", nullable = false)
    private Integer recordsUnchanged = 0;

    @Column(name = "records_preserved_human", nullable = false)
    private Integer recordsPreservedHuman = 0;

    @Column(name = "records_failed", nullable = false)
    private Integer recordsFailed = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "counts_by_status", columnDefinition = "jsonb")
    private Map<String, Object> countsByStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "estate_audit", columnDefinition = "jsonb")
    private Map<String, Object> estateAudit;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "exception_report", columnDefinition = "jsonb")
    private List<Map<String, Object>> exceptionReport;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "COMPLETED";

    @Column(name = "mapping_rule_code", length = 96)
    private String mappingRuleCode;

    @Column(name = "mapping_rule_version")
    private Integer mappingRuleVersion;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "initiated_by", length = 255)
    private String initiatedBy;

    @PrePersist
    void prePersist() {
        startedAt = startedAt == null ? Instant.now() : startedAt;
    }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }
    public Integer getRecordsTotal() { return recordsTotal; }
    public void setRecordsTotal(Integer recordsTotal) { this.recordsTotal = recordsTotal; }
    public Integer getRecordsEvaluated() { return recordsEvaluated; }
    public void setRecordsEvaluated(Integer recordsEvaluated) { this.recordsEvaluated = recordsEvaluated; }
    public Integer getRecordsCreated() { return recordsCreated; }
    public void setRecordsCreated(Integer recordsCreated) { this.recordsCreated = recordsCreated; }
    public Integer getRecordsUpdated() { return recordsUpdated; }
    public void setRecordsUpdated(Integer recordsUpdated) { this.recordsUpdated = recordsUpdated; }
    public Integer getRecordsUnchanged() { return recordsUnchanged; }
    public void setRecordsUnchanged(Integer recordsUnchanged) { this.recordsUnchanged = recordsUnchanged; }
    public Integer getRecordsPreservedHuman() { return recordsPreservedHuman; }
    public void setRecordsPreservedHuman(Integer recordsPreservedHuman) { this.recordsPreservedHuman = recordsPreservedHuman; }
    public Integer getRecordsFailed() { return recordsFailed; }
    public void setRecordsFailed(Integer recordsFailed) { this.recordsFailed = recordsFailed; }
    public Map<String, Object> getCountsByStatus() { return countsByStatus; }
    public void setCountsByStatus(Map<String, Object> countsByStatus) { this.countsByStatus = countsByStatus; }
    public Map<String, Object> getEstateAudit() { return estateAudit; }
    public void setEstateAudit(Map<String, Object> estateAudit) { this.estateAudit = estateAudit; }
    public List<Map<String, Object>> getExceptionReport() { return exceptionReport; }
    public void setExceptionReport(List<Map<String, Object>> exceptionReport) { this.exceptionReport = exceptionReport; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMappingRuleCode() { return mappingRuleCode; }
    public void setMappingRuleCode(String mappingRuleCode) { this.mappingRuleCode = mappingRuleCode; }
    public Integer getMappingRuleVersion() { return mappingRuleVersion; }
    public void setMappingRuleVersion(Integer mappingRuleVersion) { this.mappingRuleVersion = mappingRuleVersion; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }
}
