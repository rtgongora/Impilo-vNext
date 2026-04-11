package zw.gov.mohcc.impilo.tshepo.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_event", schema = "tshepo")
public class AuditEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "sequence_num", nullable = false)
    private Long sequenceNum;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Column(name = "actor_type", nullable = false)
    private String actorType;

    @Column(nullable = false)
    private String action;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "purpose_of_use", nullable = false)
    private String purposeOfUse;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(nullable = false)
    private String outcome;

    @Column(columnDefinition = "jsonb")
    private String detail;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "prev_hash")
    private String prevHash;

    @Column(name = "entry_hash", nullable = false)
    private String entryHash;

    // Health OS §22: doctrine-complete audit dimensions
    @Column(name = "device_channel")
    private String deviceChannel;

    @Column(name = "module_id")
    private String moduleId;

    // --- Getters and Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public Long getSequenceNum() { return sequenceNum; }
    public void setSequenceNum(Long sequenceNum) { this.sequenceNum = sequenceNum; }
    public UUID getCorrelationId() { return correlationId; }
    public void setCorrelationId(UUID correlationId) { this.correlationId = correlationId; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public String getPurposeOfUse() { return purposeOfUse; }
    public void setPurposeOfUse(String purposeOfUse) { this.purposeOfUse = purposeOfUse; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public String getPrevHash() { return prevHash; }
    public void setPrevHash(String prevHash) { this.prevHash = prevHash; }
    public String getEntryHash() { return entryHash; }
    public void setEntryHash(String entryHash) { this.entryHash = entryHash; }
    public String getDeviceChannel() { return deviceChannel; }
    public void setDeviceChannel(String deviceChannel) { this.deviceChannel = deviceChannel; }
    public String getModuleId() { return moduleId; }
    public void setModuleId(String moduleId) { this.moduleId = moduleId; }
}
