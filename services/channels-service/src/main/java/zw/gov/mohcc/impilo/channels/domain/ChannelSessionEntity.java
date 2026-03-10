package zw.gov.mohcc.impilo.channels.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ch_sessions")
public class ChannelSessionEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "pod_id", nullable = false, length = 64)
    private String podId;

    @Column(name = "channel_type", nullable = false, length = 32)
    private String channelType;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "subject_ref", length = 255)
    private String subjectRef;

    @Column(name = "agent_id", length = 255)
    private String agentId;

    @Column(name = "facility_id", length = 255)
    private String facilityId;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "last_activity", nullable = false)
    private OffsetDateTime lastActivity;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ChannelSessionEntity() {}

    public ChannelSessionEntity(String tenantId, String podId, String channelType) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.podId = podId;
        this.channelType = channelType;
        this.status = "ACTIVE";
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (id == null) id = UUID.randomUUID();
        if (startedAt == null) startedAt = now;
        if (lastActivity == null) lastActivity = now;
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getPodId() { return podId; }
    public String getChannelType() { return channelType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSubjectRef() { return subjectRef; }
    public void setSubjectRef(String subjectRef) { this.subjectRef = subjectRef; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getFacilityId() { return facilityId; }
    public void setFacilityId(String facilityId) { this.facilityId = facilityId; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getLastActivity() { return lastActivity; }
    public void setLastActivity(OffsetDateTime lastActivity) { this.lastActivity = lastActivity; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(OffsetDateTime closedAt) { this.closedAt = closedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
