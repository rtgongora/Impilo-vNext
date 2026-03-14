package zw.gov.mohcc.impilo.connectorfhir.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cfa_relay_audit")
public class RelayAuditEntity {
    @Id @Column(name = "audit_id") private UUID auditId;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "bundle_id", nullable = false) private String bundleId;
    @Column(name = "resource_type", nullable = false, length = 128) private String resourceType;
    @Column(name = "resource_count", nullable = false) private int resourceCount;
    @Column(name = "destination_id") private UUID destinationId;
    @Column(name = "destination_url", nullable = false, length = 512) private String destinationUrl;
    @Column(nullable = false, length = 32) private String decision = "ACCEPTED";
    @Column(nullable = false, length = 32) private String outcome = "PENDING";
    @Column(name = "error_message", columnDefinition = "TEXT") private String errorMessage;
    @Column(name = "correlation_id") private UUID correlationId;
    @Column(name = "actor_ref") private String actorRef;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "completed_at") private OffsetDateTime completedAt;

    protected RelayAuditEntity() {}
    public RelayAuditEntity(UUID auditId, UUID tenantId, String bundleId, String resourceType,
                              int resourceCount, UUID destinationId, String destinationUrl,
                              String decision, UUID correlationId, String actorRef) {
        this.auditId = auditId; this.tenantId = tenantId; this.bundleId = bundleId;
        this.resourceType = resourceType; this.resourceCount = resourceCount;
        this.destinationId = destinationId; this.destinationUrl = destinationUrl;
        this.decision = decision; this.correlationId = correlationId; this.actorRef = actorRef;
    }

    public UUID getAuditId() { return auditId; }
    public UUID getTenantId() { return tenantId; }
    public String getBundleId() { return bundleId; }
    public String getResourceType() { return resourceType; }
    public int getResourceCount() { return resourceCount; }
    public UUID getDestinationId() { return destinationId; }
    public String getDestinationUrl() { return destinationUrl; }
    public String getDecision() { return decision; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String v) { this.outcome = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { this.errorMessage = v; }
    public UUID getCorrelationId() { return correlationId; }
    public String getActorRef() { return actorRef; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime v) { this.completedAt = v; }
}
