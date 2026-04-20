package zw.gov.mohcc.impilo.ndr.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ndr_materialized_views")
public class MaterializedViewEntity {

    @Id
    @Column(name = "view_id", nullable = false, length = 26)
    private String viewId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "dataset_key", nullable = false, length = 128)
    private String datasetKey;

    @Column(name = "view_name", nullable = false, length = 256)
    private String viewName;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "row_data", nullable = false, columnDefinition = "jsonb")
    private String rowData = "{}";

    @Column(name = "refreshed_at", nullable = false)
    private OffsetDateTime refreshedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        if (refreshedAt == null) {
            refreshedAt = OffsetDateTime.now();
        }
    }

    // Getters and setters

    public String getViewId() { return viewId; }
    public void setViewId(String viewId) { this.viewId = viewId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getDatasetKey() { return datasetKey; }
    public void setDatasetKey(String datasetKey) { this.datasetKey = datasetKey; }

    public String getViewName() { return viewName; }
    public void setViewName(String viewName) { this.viewName = viewName; }

    public String getRowData() { return rowData; }
    public void setRowData(String rowData) { this.rowData = rowData; }

    public OffsetDateTime getRefreshedAt() { return refreshedAt; }
    public void setRefreshedAt(OffsetDateTime refreshedAt) { this.refreshedAt = refreshedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
