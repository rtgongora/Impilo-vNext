package zw.gov.mohcc.impilo.integration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ih_route_definitions")
public class RouteDefinitionEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "source_service", length = 128)
    private String sourceService;

    @Column(name = "event_type_prefix", length = 256)
    private String eventTypePrefix;

    @Column(name = "target_service", length = 128)
    private String targetService;

    @Column(name = "target_url", length = 512, nullable = false)
    private String targetUrl;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "pod_id", length = 64, nullable = false)
    private String podId;

    // --- v2 fields ---

    @Column(name = "match_method", length = 16)
    private String matchMethod;

    @Column(name = "match_path_regex", length = 512)
    private String matchPathRegex;

    @Lob
    @Column(name = "transform_headers_json", columnDefinition = "TEXT")
    private String transformHeadersJson;

    @Lob
    @Column(name = "transform_field_renames_json", columnDefinition = "TEXT")
    private String transformFieldRenamesJson;

    @Column(name = "target_timeout_ms")
    private Integer targetTimeoutMs;

    @Column(name = "connector_type", length = 16)
    private String connectorType = "HTTP";

    @Column(name = "mapping_template_id", length = 36)
    private String mappingTemplateId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    // --- Getters and Setters ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSourceService() {
        return sourceService;
    }

    public void setSourceService(String sourceService) {
        this.sourceService = sourceService;
    }

    public String getEventTypePrefix() {
        return eventTypePrefix;
    }

    public void setEventTypePrefix(String eventTypePrefix) {
        this.eventTypePrefix = eventTypePrefix;
    }

    public String getTargetService() {
        return targetService;
    }

    public void setTargetService(String targetService) {
        this.targetService = targetService;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getPodId() {
        return podId;
    }

    public void setPodId(String podId) {
        this.podId = podId;
    }

    public String getMatchMethod() {
        return matchMethod;
    }

    public void setMatchMethod(String matchMethod) {
        this.matchMethod = matchMethod;
    }

    public String getMatchPathRegex() {
        return matchPathRegex;
    }

    public void setMatchPathRegex(String matchPathRegex) {
        this.matchPathRegex = matchPathRegex;
    }

    public String getTransformHeadersJson() {
        return transformHeadersJson;
    }

    public void setTransformHeadersJson(String transformHeadersJson) {
        this.transformHeadersJson = transformHeadersJson;
    }

    public String getTransformFieldRenamesJson() {
        return transformFieldRenamesJson;
    }

    public void setTransformFieldRenamesJson(String transformFieldRenamesJson) {
        this.transformFieldRenamesJson = transformFieldRenamesJson;
    }

    public Integer getTargetTimeoutMs() {
        return targetTimeoutMs;
    }

    public void setTargetTimeoutMs(Integer targetTimeoutMs) {
        this.targetTimeoutMs = targetTimeoutMs;
    }

    public String getConnectorType() {
        return connectorType;
    }

    public void setConnectorType(String connectorType) {
        this.connectorType = connectorType;
    }

    public String getMappingTemplateId() {
        return mappingTemplateId;
    }

    public void setMappingTemplateId(String mappingTemplateId) {
        this.mappingTemplateId = mappingTemplateId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
