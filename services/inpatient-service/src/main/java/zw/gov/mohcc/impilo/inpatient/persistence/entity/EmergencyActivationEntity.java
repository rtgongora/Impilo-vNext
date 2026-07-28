package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "emergency_activation", schema = "inpatient")
public class EmergencyActivationEntity {

    @Id
    @Column(name = "activation_id")
    private UUID activationId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_cpid")
    private String subjectCpid;

    @Column(name = "admission_ref")
    private UUID admissionRef;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "protocol_type", nullable = false)
    private String protocolType;

    /**
     * Where in the facility this activation was called from (W6a) — distinct from
     * pct.emergency_episode.entry_route, which is how the person reached the facility at all.
     */
    @Column(name = "origin")
    private String origin;

    /**
     * Cross-reference to pct.emergency_episode (a different service/database — no FK, same pattern
     * as trauma_episode_id). Lets an in-facility deterioration mint on the admission's existing
     * journey rather than opening a second one.
     */
    @Column(name = "emergency_episode_id")
    private UUID emergencyEpisodeId;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "outcome")
    private String outcome;

    @Column(name = "team_leader")
    private String teamLeader;

    @Column(name = "location")
    private String location;

    @Column(name = "notes")
    private String notes;

    @Column(name = "activation_time")
    private OffsetDateTime activationTime;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @PrePersist
    void onCreate() {
        if (activationId == null) activationId = UUID.randomUUID();
        if (activationTime == null) activationTime = OffsetDateTime.now();
    }

    public UUID getActivationId() { return activationId; }
    public void setActivationId(UUID activationId) { this.activationId = activationId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String subjectCpid) { this.subjectCpid = subjectCpid; }
    public UUID getAdmissionRef() { return admissionRef; }
    public void setAdmissionRef(UUID admissionRef) { this.admissionRef = admissionRef; }
    public UUID getEncounterId() { return encounterId; }
    public void setEncounterId(UUID encounterId) { this.encounterId = encounterId; }
    public String getProtocolType() { return protocolType; }
    public void setProtocolType(String protocolType) { this.protocolType = protocolType; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public UUID getEmergencyEpisodeId() { return emergencyEpisodeId; }
    public void setEmergencyEpisodeId(UUID emergencyEpisodeId) { this.emergencyEpisodeId = emergencyEpisodeId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getTeamLeader() { return teamLeader; }
    public void setTeamLeader(String teamLeader) { this.teamLeader = teamLeader; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public OffsetDateTime getActivationTime() { return activationTime; }
    public void setActivationTime(OffsetDateTime activationTime) { this.activationTime = activationTime; }
    public OffsetDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(OffsetDateTime endedAt) { this.endedAt = endedAt; }
}
