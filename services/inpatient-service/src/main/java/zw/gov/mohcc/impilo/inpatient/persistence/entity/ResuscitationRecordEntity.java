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

    /**
     * Denormalised from emergency_activation.tenant_id (W6a). Immutable — never repointed, so this
     * needs no identity-repoint hook, unlike subject_cpid which deliberately stays only on the
     * activation (see InpatientResuscitationRepointHook).
     */
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Canonical trauma-episode correlation id (DAIDZAI-minted). Re-keyed onto the episode in W5. */
    @Column(name = "trauma_episode_id")
    private UUID traumaEpisodeId;

    /** DERIVED since W6b — recomputed from resuscitation_event, never client-set. See V201. */
    @Column(name = "cpr_cycles")
    private Integer cprCycles;

    /** DERIVED since W6b — recomputed from resuscitation_event, never client-set. See V201. */
    @Column(name = "defibrillations")
    private Integer defibrillations;

    /** DERIVED since W6b — the first RHYTHM event's value. See V201. */
    @Column(name = "initial_rhythm")
    private String initialRhythm;

    /** Stamped only at stand-down, from the latest RHYTHM event at that time. See V201. */
    @Column(name = "final_rhythm")
    private String finalRhythm;

    /** DERIVED since W6b — recomputed from resuscitation_event, never client-set. See V201. */
    @Column(name = "rosc_achieved")
    private Boolean roscAchieved;

    @Column(name = "rosc_time")
    private OffsetDateTime roscTime;

    @Column(name = "medications_json")
    private String medicationsJson;

    // ── Header scalars (V201, W6b) — the ones @Version actually protects. ──────────────────────

    @Column(name = "team_leader")
    private String teamLeader;

    /** The latest known rhythm — distinct from initial_rhythm/final_rhythm, both derived/stamped. */
    @Column(name = "current_rhythm")
    private String currentRhythm;

    @Column(name = "airway_status")
    private String airwayStatus;

    @Column(name = "access_status")
    private String accessStatus;

    @Column(name = "outcome")
    private String outcome;

    @Column(name = "stand_down_reason")
    private String standDownReason;

    /** The header-only optimistic lock. Never applied to the append-only event time-series. */
    @jakarta.persistence.Version
    @Column(name = "record_version", nullable = false)
    private Long recordVersion;

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
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getTraumaEpisodeId() { return traumaEpisodeId; }
    public void setTraumaEpisodeId(UUID traumaEpisodeId) { this.traumaEpisodeId = traumaEpisodeId; }
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
    public String getTeamLeader() { return teamLeader; }
    public void setTeamLeader(String teamLeader) { this.teamLeader = teamLeader; }
    public String getCurrentRhythm() { return currentRhythm; }
    public void setCurrentRhythm(String currentRhythm) { this.currentRhythm = currentRhythm; }
    public String getAirwayStatus() { return airwayStatus; }
    public void setAirwayStatus(String airwayStatus) { this.airwayStatus = airwayStatus; }
    public String getAccessStatus() { return accessStatus; }
    public void setAccessStatus(String accessStatus) { this.accessStatus = accessStatus; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getStandDownReason() { return standDownReason; }
    public void setStandDownReason(String standDownReason) { this.standDownReason = standDownReason; }
    public Long getRecordVersion() { return recordVersion; }
    public void setRecordVersion(Long recordVersion) { this.recordVersion = recordVersion; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
