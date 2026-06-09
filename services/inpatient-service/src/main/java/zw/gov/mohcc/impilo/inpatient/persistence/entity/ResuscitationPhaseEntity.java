package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "resuscitation_phase", schema = "inpatient")
public class ResuscitationPhaseEntity {

    @Id
    @Column(name = "phase_id")
    private UUID phaseId;

    @Column(name = "activation_id", nullable = false)
    private UUID activationId;

    @Column(name = "phase_type", nullable = false)
    private String phaseType;

    @Column(name = "rhythm")
    private String rhythm;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "notes")
    private String notes;

    @PrePersist
    void onCreate() {
        if (phaseId == null) phaseId = UUID.randomUUID();
        if (startedAt == null) startedAt = OffsetDateTime.now();
    }

    public UUID getPhaseId() { return phaseId; }
    public void setPhaseId(UUID phaseId) { this.phaseId = phaseId; }
    public UUID getActivationId() { return activationId; }
    public void setActivationId(UUID activationId) { this.activationId = activationId; }
    public String getPhaseType() { return phaseType; }
    public void setPhaseType(String phaseType) { this.phaseType = phaseType; }
    public String getRhythm() { return rhythm; }
    public void setRhythm(String rhythm) { this.rhythm = rhythm; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(OffsetDateTime endedAt) { this.endedAt = endedAt; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
