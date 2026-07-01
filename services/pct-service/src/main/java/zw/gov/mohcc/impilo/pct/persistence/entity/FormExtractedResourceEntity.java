package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Provenance record for one clinical resource extracted from a form response. */
@Entity
@Table(name = "pct_form_extracted_resource")
public class FormExtractedResourceEntity {

    @Id
    @Column(name = "extracted_id", nullable = false)
    private UUID extractedId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "response_id", nullable = false)
    private UUID responseId;

    @Column(name = "form_schema_version_id", nullable = false)
    private String formSchemaVersionId;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "route_target", nullable = false)
    private String routeTarget;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_link_ids", nullable = false, columnDefinition = "jsonb")
    private String sourceLinkIds = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resource_payload", columnDefinition = "jsonb")
    private String resourcePayload;

    @Column(name = "external_ref")
    private String externalRef;

    @Column(name = "local_ref")
    private String localRef;

    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (extractedId == null) {
            extractedId = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getExtractedId() { return extractedId; }
    public void setExtractedId(UUID extractedId) { this.extractedId = extractedId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getResponseId() { return responseId; }
    public void setResponseId(UUID responseId) { this.responseId = responseId; }

    public String getFormSchemaVersionId() { return formSchemaVersionId; }
    public void setFormSchemaVersionId(String formSchemaVersionId) { this.formSchemaVersionId = formSchemaVersionId; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public String getRouteTarget() { return routeTarget; }
    public void setRouteTarget(String routeTarget) { this.routeTarget = routeTarget; }

    public String getSourceLinkIds() { return sourceLinkIds; }
    public void setSourceLinkIds(String sourceLinkIds) { this.sourceLinkIds = sourceLinkIds; }

    public String getResourcePayload() { return resourcePayload; }
    public void setResourcePayload(String resourcePayload) { this.resourcePayload = resourcePayload; }

    public String getExternalRef() { return externalRef; }
    public void setExternalRef(String externalRef) { this.externalRef = externalRef; }

    public String getLocalRef() { return localRef; }
    public void setLocalRef(String localRef) { this.localRef = localRef; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
