package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "resuscitation_record", schema = "inpatient")
public class ResuscitationRecordEntity {

    @Id
    @Column(name = "resus_id")
    private UUID resusId;

    @Column(name = "activation_id", nullable = false)
    private UUID activationId;

    @Column(name = "cpr_cycles")
    private Integer cprCycles;

    @Column(name = "defibrillations")
    private Integer defibrillations;

    @Column(name = "initial_rhythm")
    private String initialRhythm;

    @Column(name = "final_rhythm")
    private String finalRhythm;

    @Column(name = "rosc_achieved")
    private Boolean roscAchieved;

    @Column(name = "rosc_time")
    private OffsetDateTime roscTime;

    @Column(name = "medications_json")
    private String medicationsJson;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (resusId == null) resusId = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getResusId() { return resusId; }
    public void setResusId(UUID resusId) { this.resusId = resusId; }
    public UUID getActivationId() { return activationId; }
    public void setActivationId(UUID activationId) { this.activationId = activationId; }
    public Integer getCprCycles() { return cprCycles; }
    public void setCprCycles(Integer cprCycles) { this.cprCycles = cprCycles; }
    public Integer getDefibrillations() { return defibrillations; }
    public void setDefibrillations(Integer defibrillations) { this.defibrillations = defibrillations; }
    public String getInitialRhythm() { return initialRhythm; }
    public void setInitialRhythm(String initialRhythm) { this.initialRhythm = initialRhythm; }
    public String getFinalRhythm() { return finalRhythm; }
    public void setFinalRhythm(String finalRhythm) { this.finalRhythm = finalRhythm; }
    public Boolean getRoscAchieved() { return roscAchieved; }
    public void setRoscAchieved(Boolean roscAchieved) { this.roscAchieved = roscAchieved; }
    public OffsetDateTime getRoscTime() { return roscTime; }
    public void setRoscTime(OffsetDateTime roscTime) { this.roscTime = roscTime; }
    public String getMedicationsJson() { return medicationsJson; }
    public void setMedicationsJson(String medicationsJson) { this.medicationsJson = medicationsJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
