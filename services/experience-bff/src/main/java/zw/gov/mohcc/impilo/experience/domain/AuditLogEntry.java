package zw.gov.mohcc.impilo.experience.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Column(nullable = false)
    private String action;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(columnDefinition = "jsonb")
    private String details;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    protected AuditLogEntry() {}

    public static AuditLogEntry create(String tenantId, String actorId, String action,
                                        String resourceType, String resourceId,
                                        String details, String ipAddress) {
        AuditLogEntry e = new AuditLogEntry();
        e.tenantId = tenantId;
        e.actorId = actorId;
        e.action = action;
        e.resourceType = resourceType;
        e.resourceId = resourceId;
        e.details = details;
        e.ipAddress = ipAddress;
        e.occurredAt = OffsetDateTime.now();
        return e;
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getActorId() { return actorId; }
    public String getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public String getDetails() { return details; }
    public String getIpAddress() { return ipAddress; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
}
