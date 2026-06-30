package zw.gov.mohcc.impilo.simba.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A coaching touchpoint (check-in, session, note, escalation). Notes are NOT clinical records. */
@Entity
@Table(name = "coaching_touchpoints", schema = "simba")
public class CoachingTouchpointEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "touchpoint_id", nullable = false, unique = true)
    private UUID touchpointId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "relationship_id", nullable = false)
    private UUID relationshipId;

    @Column(name = "touchpoint_type", nullable = false, length = 32)
    private String touchpointType = "CHECKIN";

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "note")
    private String note;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (touchpointId == null) {
            touchpointId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getTouchpointId() { return touchpointId; }
    public void setTouchpointId(UUID touchpointId) { this.touchpointId = touchpointId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getRelationshipId() { return relationshipId; }
    public void setRelationshipId(UUID relationshipId) { this.relationshipId = relationshipId; }
    public String getTouchpointType() { return touchpointType; }
    public void setTouchpointType(String touchpointType) { this.touchpointType = touchpointType; }
    public OffsetDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(OffsetDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
