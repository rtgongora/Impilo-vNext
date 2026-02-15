package zw.gov.mohcc.impilo.ndr.persistence.entity;

import jakarta.persistence.*;
import zw.gov.mohcc.impilo.ndr.domain.DatasetStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ndr_datasets")
public class DatasetEntity {

    @Id
    @Column(name = "dataset_id", nullable = false, length = 26)
    private String datasetId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "dataset_key", nullable = false, length = 128)
    private String datasetKey;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "owner", length = 128)
    private String owner;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private DatasetStatus status = DatasetStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and setters

    public String getDatasetId() { return datasetId; }
    public void setDatasetId(String datasetId) { this.datasetId = datasetId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getDatasetKey() { return datasetKey; }
    public void setDatasetKey(String datasetKey) { this.datasetKey = datasetKey; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public DatasetStatus getStatus() { return status; }
    public void setStatus(DatasetStatus status) { this.status = status; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
