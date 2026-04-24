package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "lrn_learning_resource")
public class LearningResourceEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "resource_code", nullable = false, length = 160)
    private String resourceCode;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "title", nullable = false, length = 512)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "resource_type", nullable = false, length = 64)
    private String resourceType;

    @Column(name = "external_ref", length = 512)
    private String externalRef;

    /** Moodle course-module id when this resource maps to a specific CM (optional). */
    @Column(name = "moodle_cm_id")
    private Long moodleCmId;

    @Column(name = "app_code", length = 64)
    private String appCode;

    @Column(name = "role_tags", columnDefinition = "TEXT")
    private String roleTags;

    @Column(name = "workflow_tags", columnDefinition = "TEXT")
    private String workflowTags;

    @Column(name = "lifecycle_tags", columnDefinition = "TEXT")
    private String lifecycleTags;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getResourceCode() {
        return resourceCode;
    }

    public void setResourceCode(String resourceCode) {
        this.resourceCode = resourceCode;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public void setExternalRef(String externalRef) {
        this.externalRef = externalRef;
    }

    public Long getMoodleCmId() {
        return moodleCmId;
    }

    public void setMoodleCmId(Long moodleCmId) {
        this.moodleCmId = moodleCmId;
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public String getRoleTags() {
        return roleTags;
    }

    public void setRoleTags(String roleTags) {
        this.roleTags = roleTags;
    }

    public String getWorkflowTags() {
        return workflowTags;
    }

    public void setWorkflowTags(String workflowTags) {
        this.workflowTags = workflowTags;
    }

    public String getLifecycleTags() {
        return lifecycleTags;
    }

    public void setLifecycleTags(String lifecycleTags) {
        this.lifecycleTags = lifecycleTags;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
