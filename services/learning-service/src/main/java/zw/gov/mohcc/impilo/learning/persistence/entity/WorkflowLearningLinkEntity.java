package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "lrn_workflow_learning_link")
public class WorkflowLearningLinkEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "app_code", nullable = false, length = 64)
    private String appCode;

    @Column(name = "workflow_code", length = 128)
    private String workflowCode;

    @Column(name = "screen_or_route_ref", length = 512)
    private String screenOrRouteRef;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "path_id")
    private UUID pathId;

    @Column(name = "link_type", nullable = false, length = 64)
    private String linkType;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

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

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public String getWorkflowCode() {
        return workflowCode;
    }

    public void setWorkflowCode(String workflowCode) {
        this.workflowCode = workflowCode;
    }

    public String getScreenOrRouteRef() {
        return screenOrRouteRef;
    }

    public void setScreenOrRouteRef(String screenOrRouteRef) {
        this.screenOrRouteRef = screenOrRouteRef;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public void setResourceId(UUID resourceId) {
        this.resourceId = resourceId;
    }

    public UUID getPathId() {
        return pathId;
    }

    public void setPathId(UUID pathId) {
        this.pathId = pathId;
    }

    public String getLinkType() {
        return linkType;
    }

    public void setLinkType(String linkType) {
        this.linkType = linkType;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
