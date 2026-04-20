package zw.gov.mohcc.impilo.gl.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "trial_balance_snapshots", schema = "gl")
public class TrialBalanceSnapshotEntity {

    @Id
    @Column(name = "snapshot_id")
    private UUID snapshotId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "fiscal_period_id", nullable = false)
    private UUID fiscalPeriodId;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "snapshot_json", nullable = false, columnDefinition = "jsonb")
    private String snapshotJson;

    @PrePersist
    void prePersist() {
        if (snapshotId == null) {
            snapshotId = UUID.randomUUID();
        }
        if (generatedAt == null) {
            generatedAt = OffsetDateTime.now();
        }
    }

    public UUID getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(UUID snapshotId) {
        this.snapshotId = snapshotId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getFiscalPeriodId() {
        return fiscalPeriodId;
    }

    public void setFiscalPeriodId(UUID fiscalPeriodId) {
        this.fiscalPeriodId = fiscalPeriodId;
    }

    public OffsetDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(OffsetDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }
}
