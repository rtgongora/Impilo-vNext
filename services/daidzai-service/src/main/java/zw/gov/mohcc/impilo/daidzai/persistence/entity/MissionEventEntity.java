package zw.gov.mohcc.impilo.daidzai.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dai_mission_event", schema = "daidzai")
public class MissionEventEntity {

    @Id @Column(name = "id") private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "incident_id", nullable = false) private UUID incidentId;
    @Column(name = "status", nullable = false, length = 24) private String status;
    @Column(name = "nhume_mission_ref", length = 128) private String nhumeMissionRef;
    @Column(name = "note", length = 1024) private String note;
    @Column(name = "actor_id", length = 128) private String actorId;
    @Column(name = "actor_type", length = 48) private String actorType;
    @Column(name = "occurred_at", nullable = false) private OffsetDateTime occurredAt;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;

    @PrePersist void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (occurredAt == null) occurredAt = now;
        if (createdAt == null) createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getIncidentId() { return incidentId; }
    public void setIncidentId(UUID v) { this.incidentId = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getNhumeMissionRef() { return nhumeMissionRef; }
    public void setNhumeMissionRef(String v) { this.nhumeMissionRef = v; }
    public String getNote() { return note; }
    public void setNote(String v) { this.note = v; }
    public String getActorId() { return actorId; }
    public void setActorId(String v) { this.actorId = v; }
    public String getActorType() { return actorType; }
    public void setActorType(String v) { this.actorType = v; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime v) { this.occurredAt = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
