package zw.gov.mohcc.impilo.datagovernance.deid.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * De-identification run ledger row. Lifecycle is the {@link DeidRunStatus} FSM;
 * every state change is audited through the outbox
 * ({@code impilo.data.governance.deid.run.*} events).
 */
@Entity
@Table(name = "deid_run")
public class DeidRunEntity {

    @Id
    @Column(name = "run_id", nullable = false)
    private UUID runId = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "dataset_id", nullable = false)
    private UUID datasetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private DeidRunStatus status = DeidRunStatus.REQUESTED;

    @Column(name = "rows_in", nullable = false)
    private int rowsIn;

    @Column(name = "rows_out", nullable = false)
    private int rowsOut;

    @Column(name = "rows_suppressed", nullable = false)
    private int rowsSuppressed;

    @Column(name = "cells_suppressed", nullable = false)
    private int cellsSuppressed;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "qa_report_json", columnDefinition = "jsonb")
    private String qaReportJson;

    @Column(name = "policy_id")
    private UUID policyId;

    @Column(name = "policy_version", length = 64)
    private String policyVersion;

    @Column(name = "requested_by", nullable = false, length = 255)
    private String requestedBy;

    @Column(name = "correlation_id", length = 255)
    private String correlationId;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt = OffsetDateTime.now();

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected DeidRunEntity() {}

    public DeidRunEntity(UUID tenantId, UUID datasetId, String requestedBy, String correlationId) {
        this.tenantId = tenantId;
        this.datasetId = datasetId;
        this.requestedBy = requestedBy;
        this.correlationId = correlationId;
    }

    public UUID getRunId() { return runId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getDatasetId() { return datasetId; }
    public DeidRunStatus getStatus() { return status; }
    public int getRowsIn() { return rowsIn; }
    public int getRowsOut() { return rowsOut; }
    public int getRowsSuppressed() { return rowsSuppressed; }
    public int getCellsSuppressed() { return cellsSuppressed; }
    public String getRejectionReason() { return rejectionReason; }
    public String getQaReportJson() { return qaReportJson; }
    public UUID getPolicyId() { return policyId; }
    public String getPolicyVersion() { return policyVersion; }
    public String getRequestedBy() { return requestedBy; }
    public String getCorrelationId() { return correlationId; }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setStatus(DeidRunStatus status) { this.status = status; }
    public void setRowsIn(int rowsIn) { this.rowsIn = rowsIn; }
    public void setRowsOut(int rowsOut) { this.rowsOut = rowsOut; }
    public void setRowsSuppressed(int rowsSuppressed) { this.rowsSuppressed = rowsSuppressed; }
    public void setCellsSuppressed(int cellsSuppressed) { this.cellsSuppressed = cellsSuppressed; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public void setQaReportJson(String qaReportJson) { this.qaReportJson = qaReportJson; }
    public void setPolicyId(UUID policyId) { this.policyId = policyId; }
    public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
