package zw.gov.mohcc.impilo.obs.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "service_heartbeats", schema = "obs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"service_name", "instance_id"}))
public class ServiceHeartbeatEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_name", nullable = false, length = 128)
    private String serviceName;

    @Column(name = "instance_id", nullable = false, length = 255)
    private String instanceId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 32)
    private String status = "UP";

    @Column(name = "version_tag", length = 64)
    private String versionTag;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String metadata = "{}";

    @Column(name = "last_heartbeat", nullable = false)
    private OffsetDateTime lastHeartbeat;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        if (lastHeartbeat == null) lastHeartbeat = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String v) { this.serviceName = v; }
    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String v) { this.instanceId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getVersionTag() { return versionTag; }
    public void setVersionTag(String v) { this.versionTag = v; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String v) { this.metadata = v; }
    public OffsetDateTime getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(OffsetDateTime v) { this.lastHeartbeat = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
