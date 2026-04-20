package zw.gov.mohcc.impilo.ndr.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.ndr.domain.VersionStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ndr_dataset_versions")
public class DatasetVersionEntity {

    @Id
    @Column(name = "version_id", nullable = false, length = 26)
    private String versionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "dataset_id", nullable = false, length = 26)
    private String datasetId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "schema_json", nullable = false, columnDefinition = "jsonb")
    private String schemaJson = "{}";

    @Column(name = "row_count", nullable = false)
    private long rowCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private VersionStatus status = VersionStatus.DRAFT;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // Getters and setters

    public String getVersionId() { return versionId; }
    public void setVersionId(String versionId) { this.versionId = versionId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getDatasetId() { return datasetId; }
    public void setDatasetId(String datasetId) { this.datasetId = datasetId; }

    public int getVersionNumber() { return versionNumber; }
    public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }

    public String getSchemaJson() { return schemaJson; }
    public void setSchemaJson(String schemaJson) { this.schemaJson = schemaJson; }

    public long getRowCount() { return rowCount; }
    public void setRowCount(long rowCount) { this.rowCount = rowCount; }

    public VersionStatus getStatus() { return status; }
    public void setStatus(VersionStatus status) { this.status = status; }

    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
