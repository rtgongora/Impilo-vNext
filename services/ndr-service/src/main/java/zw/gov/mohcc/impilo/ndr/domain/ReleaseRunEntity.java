package zw.gov.mohcc.impilo.ndr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * NDR release-zone build record. One row per {@code POST /internal/v1/ndr/release/{datasetRef}}
 * (or scheduled build). Links the materialised analytics zone back to the de-identification
 * run and manifest that produced it, and records the terminal outcome — a build is only
 * RELEASED when the de-id engine's policy gate authorised the run.
 */
@Entity
@Table(name = "ndr_release_run")
public class ReleaseRunEntity {

    @Id
    @Column(name = "release_id", nullable = false)
    private UUID releaseId = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "dataset_ref", nullable = false, length = 128)
    private String datasetRef;

    @Column(name = "deid_run_id")
    private UUID deidRunId;

    @Column(name = "manifest_id")
    private UUID manifestId;

    @Column(name = "policy_version", length = 64)
    private String policyVersion;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "rejection_reason", length = 512)
    private String rejectionReason;

    @Column(name = "rows_in", nullable = false)
    private int rowsIn = 0;

    @Column(name = "rows_out", nullable = false)
    private int rowsOut = 0;

    @Column(name = "rows_suppressed", nullable = false)
    private int rowsSuppressed = 0;

    @Column(name = "correlation_id", length = 255)
    private String correlationId;

    @Column(name = "built_at", nullable = false)
    private OffsetDateTime builtAt = OffsetDateTime.now();

    public ReleaseRunEntity() {}

    public ReleaseRunEntity(UUID tenantId, String datasetRef, String correlationId) {
        this.tenantId = tenantId;
        this.datasetRef = datasetRef;
        this.correlationId = correlationId;
    }

    public UUID getReleaseId() { return releaseId; }
    public UUID getTenantId() { return tenantId; }
    public String getDatasetRef() { return datasetRef; }
    public UUID getDeidRunId() { return deidRunId; }
    public UUID getManifestId() { return manifestId; }
    public String getPolicyVersion() { return policyVersion; }
    public String getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
    public int getRowsIn() { return rowsIn; }
    public int getRowsOut() { return rowsOut; }
    public int getRowsSuppressed() { return rowsSuppressed; }
    public String getCorrelationId() { return correlationId; }
    public OffsetDateTime getBuiltAt() { return builtAt; }

    public void setReleaseId(UUID releaseId) { this.releaseId = releaseId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public void setDatasetRef(String datasetRef) { this.datasetRef = datasetRef; }
    public void setDeidRunId(UUID deidRunId) { this.deidRunId = deidRunId; }
    public void setManifestId(UUID manifestId) { this.manifestId = manifestId; }
    public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }
    public void setStatus(String status) { this.status = status; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public void setRowsIn(int rowsIn) { this.rowsIn = rowsIn; }
    public void setRowsOut(int rowsOut) { this.rowsOut = rowsOut; }
    public void setRowsSuppressed(int rowsSuppressed) { this.rowsSuppressed = rowsSuppressed; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public void setBuiltAt(OffsetDateTime builtAt) { this.builtAt = builtAt; }
}
