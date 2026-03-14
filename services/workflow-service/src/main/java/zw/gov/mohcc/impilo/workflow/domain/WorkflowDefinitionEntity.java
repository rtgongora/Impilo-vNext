package zw.gov.mohcc.impilo.workflow.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "wf_definitions")
public class WorkflowDefinitionEntity {
    @Id @Column(name = "definition_id") private UUID definitionId;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(nullable = false, length = 128) private String code;
    @Column(nullable = false) private String name;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(length = 64) private String category = "GENERAL";
    @Column(nullable = false) private int version = 1;
    @Column(nullable = false, length = 32) private String status = "DRAFT";
    @Column(name = "schema_json", columnDefinition = "TEXT") private String schemaJson;
    @Column(name = "steps_json", nullable = false, columnDefinition = "TEXT") private String stepsJson;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt = OffsetDateTime.now();
    @Column(name = "published_at") private OffsetDateTime publishedAt;

    protected WorkflowDefinitionEntity() {}
    public WorkflowDefinitionEntity(UUID definitionId, UUID tenantId, String code, String name,
                                     String description, String category, int version, String stepsJson) {
        this.definitionId = definitionId; this.tenantId = tenantId; this.code = code; this.name = name;
        this.description = description; this.category = category != null ? category : "GENERAL";
        this.version = version; this.stepsJson = stepsJson;
    }

    public UUID getDefinitionId() { return definitionId; }
    public UUID getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public int getVersion() { return version; }
    public String getStatus() { return status; }
    public void setStatus(String s) { this.status = s; }
    public String getSchemaJson() { return schemaJson; }
    public void setSchemaJson(String s) { this.schemaJson = s; }
    public String getStepsJson() { return stepsJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime t) { this.updatedAt = t; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime t) { this.publishedAt = t; }
}
