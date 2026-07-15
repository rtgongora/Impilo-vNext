package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A resolved on-call trauma-team member for a trauma activation. One row per member resolved from
 * the VASHANDI on-duty pool (the designated trauma panel — a projection over the workforce SoR).
 * Carries the real notification delivery-intent ref + the ack/escalation lifecycle, replacing the
 * old jsonb team blob + PENDING→SENT paging.
 */
@Entity
@Table(name = "ed_trauma_team_member")
public class EdTraumaTeamMemberEntity {

    @Id @Column(name = "id") private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "trauma_id", nullable = false) private UUID traumaId;
    @Column(name = "visit_id", nullable = false) private UUID visitId;
    @Column(name = "trauma_episode_id") private UUID traumaEpisodeId;
    @Column(name = "workforce_profile_id", nullable = false) private String workforceProfileId;
    @Column(name = "role") private String role;
    @Column(name = "shift_id") private String shiftId;
    @Column(name = "notification_ref") private String notificationRef;
    @Column(name = "notified_at") private OffsetDateTime notifiedAt;
    @Column(name = "ack_status", nullable = false) private String ackStatus = "PENDING";
    @Column(name = "ack_at") private OffsetDateTime ackAt;
    @Column(name = "ack_by") private String ackBy;
    @Column(name = "escalated_at") private OffsetDateTime escalatedAt;
    @Column(name = "escalation_ref") private String escalationRef;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;

    @PrePersist void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getTraumaId() { return traumaId; }
    public void setTraumaId(UUID v) { this.traumaId = v; }
    public UUID getVisitId() { return visitId; }
    public void setVisitId(UUID v) { this.visitId = v; }
    public UUID getTraumaEpisodeId() { return traumaEpisodeId; }
    public void setTraumaEpisodeId(UUID v) { this.traumaEpisodeId = v; }
    public String getWorkforceProfileId() { return workforceProfileId; }
    public void setWorkforceProfileId(String v) { this.workforceProfileId = v; }
    public String getRole() { return role; }
    public void setRole(String v) { this.role = v; }
    public String getShiftId() { return shiftId; }
    public void setShiftId(String v) { this.shiftId = v; }
    public String getNotificationRef() { return notificationRef; }
    public void setNotificationRef(String v) { this.notificationRef = v; }
    public OffsetDateTime getNotifiedAt() { return notifiedAt; }
    public void setNotifiedAt(OffsetDateTime v) { this.notifiedAt = v; }
    public String getAckStatus() { return ackStatus; }
    public void setAckStatus(String v) { this.ackStatus = v; }
    public OffsetDateTime getAckAt() { return ackAt; }
    public void setAckAt(OffsetDateTime v) { this.ackAt = v; }
    public String getAckBy() { return ackBy; }
    public void setAckBy(String v) { this.ackBy = v; }
    public OffsetDateTime getEscalatedAt() { return escalatedAt; }
    public void setEscalatedAt(OffsetDateTime v) { this.escalatedAt = v; }
    public String getEscalationRef() { return escalationRef; }
    public void setEscalationRef(String v) { this.escalationRef = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
