package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "lrn_contextual_learning_hint")
public class ContextualLearningHintEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "app_code", nullable = false, length = 64)
    private String appCode;

    @Column(name = "route_or_component_ref", nullable = false, length = 512)
    private String routeOrComponentRef;

    @Column(name = "hint_type", nullable = false, length = 64)
    private String hintType;

    @Column(name = "text_summary", nullable = false, length = 2000)
    private String textSummary;

    @Column(name = "resource_id")
    private UUID resourceId;

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

    public String getRouteOrComponentRef() {
        return routeOrComponentRef;
    }

    public void setRouteOrComponentRef(String routeOrComponentRef) {
        this.routeOrComponentRef = routeOrComponentRef;
    }

    public String getHintType() {
        return hintType;
    }

    public void setHintType(String hintType) {
        this.hintType = hintType;
    }

    public String getTextSummary() {
        return textSummary;
    }

    public void setTextSummary(String textSummary) {
        this.textSummary = textSummary;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public void setResourceId(UUID resourceId) {
        this.resourceId = resourceId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
