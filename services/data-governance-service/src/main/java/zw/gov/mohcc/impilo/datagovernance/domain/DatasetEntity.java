package zw.gov.mohcc.impilo.datagovernance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dgv_dataset")
public class DatasetEntity {

    @Id
    @Column(name = "dataset_id", nullable = false)
    private UUID datasetId = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "classification", nullable = false, length = 64)
    private String classification = "INTERNAL";

    @Column(name = "description", length = 1024)
    private String description;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected DatasetEntity() {}

    public DatasetEntity(UUID tenantId, String name, String classification, String description) {
        this.tenantId = tenantId;
        this.name = name;
        this.classification = classification != null ? classification : "INTERNAL";
        this.description = description;
    }

    public UUID getDatasetId() { return datasetId; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getClassification() { return classification; }
    public String getDescription() { return description; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setDatasetId(UUID datasetId) { this.datasetId = datasetId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public void setName(String name) { this.name = name; }
    public void setClassification(String classification) { this.classification = classification; }
    public void setDescription(String description) { this.description = description; }
}
