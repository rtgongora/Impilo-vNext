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
 * Release manifest — written exactly once per {@code RELEASED} run. Holds
 * column names and suppression statistics only, never row values; downstream
 * stores (NDR / data-warehouse) materialise against this manifest and the
 * {@code impilo.data.governance.deid.run.released.v1} outbox event.
 */
@Entity
@Table(name = "deid_release_manifest")
public class DeidReleaseManifestEntity {

    @Id
    @Column(name = "manifest_id", nullable = false)
    private UUID manifestId = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "dataset_id", nullable = false)
    private UUID datasetId;

    @Column(name = "dataset_code", nullable = false, length = 64)
    private String datasetCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone_target", nullable = false, length = 16)
    private DeidZone zoneTarget;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "k_threshold", nullable = false)
    private int kThreshold;

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(name = "policy_version", length = 64)
    private String policyVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "columns_json", columnDefinition = "jsonb")
    private String columnsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "suppression_stats_json", columnDefinition = "jsonb")
    private String suppressionStatsJson;

    @Column(name = "released_at", nullable = false)
    private OffsetDateTime releasedAt = OffsetDateTime.now();

    protected DeidReleaseManifestEntity() {}

    public DeidReleaseManifestEntity(UUID tenantId, UUID runId, UUID datasetId, String datasetCode,
                                     DeidZone zoneTarget, int rowCount, int kThreshold,
                                     UUID policyId, String policyVersion) {
        this.tenantId = tenantId;
        this.runId = runId;
        this.datasetId = datasetId;
        this.datasetCode = datasetCode;
        this.zoneTarget = zoneTarget;
        this.rowCount = rowCount;
        this.kThreshold = kThreshold;
        this.policyId = policyId;
        this.policyVersion = policyVersion;
    }

    public UUID getManifestId() { return manifestId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getRunId() { return runId; }
    public UUID getDatasetId() { return datasetId; }
    public String getDatasetCode() { return datasetCode; }
    public DeidZone getZoneTarget() { return zoneTarget; }
    public int getRowCount() { return rowCount; }
    public int getKThreshold() { return kThreshold; }
    public UUID getPolicyId() { return policyId; }
    public String getPolicyVersion() { return policyVersion; }
    public String getColumnsJson() { return columnsJson; }
    public String getSuppressionStatsJson() { return suppressionStatsJson; }
    public OffsetDateTime getReleasedAt() { return releasedAt; }

    public void setColumnsJson(String columnsJson) { this.columnsJson = columnsJson; }
    public void setSuppressionStatsJson(String suppressionStatsJson) { this.suppressionStatsJson = suppressionStatsJson; }
}
