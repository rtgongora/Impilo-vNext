package zw.gov.mohcc.impilo.daidzai.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An EMS clinical-dispatch mission — a crew dispatched to a patient, tracked through its state
 * machine to a facility handover. Binds to the DAIDZAI incident and the canonical trauma episode.
 * One clinical mission per incident (UNIQUE(tenant, incident)) makes dispatch idempotent.
 */
@Entity
@Table(name = "dai_ems_mission", schema = "daidzai",
        uniqueConstraints = @UniqueConstraint(name = "uq_dai_ems_mission_incident",
                columnNames = {"tenant_id", "incident_id"}))
public class EmsMissionEntity {

    @Id @Column(name = "id") private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "mission_reference", nullable = false, length = 48) private String missionReference;
    @Column(name = "incident_id", nullable = false) private UUID incidentId;
    @Column(name = "trauma_episode_id") private UUID traumaEpisodeId;
    @Column(name = "unit_id") private UUID unitId;
    @Column(name = "state", nullable = false, length = 32) private String state = "CREATED";
    @Column(name = "dispatch_priority", length = 16) private String dispatchPriority;
    @Column(name = "destination_facility_id") private UUID destinationFacilityId;
    @Column(name = "destination_eta_seconds") private Integer destinationEtaSeconds;
    @Column(name = "pct_encounter_ref", length = 128) private String pctEncounterRef;
    @Column(name = "standdown_reason", length = 255) private String standdownReason;
    @Column(name = "dispatched_at") private OffsetDateTime dispatchedAt;
    @Column(name = "on_scene_at") private OffsetDateTime onSceneAt;
    @Column(name = "departed_scene_at") private OffsetDateTime departedSceneAt;
    @Column(name = "arrived_facility_at") private OffsetDateTime arrivedFacilityAt;
    @Column(name = "handover_at") private OffsetDateTime handoverAt;
    @Column(name = "cleared_at") private OffsetDateTime clearedAt;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    @PrePersist void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getMissionReference() { return missionReference; }
    public void setMissionReference(String v) { this.missionReference = v; }
    public UUID getIncidentId() { return incidentId; }
    public void setIncidentId(UUID v) { this.incidentId = v; }
    public UUID getTraumaEpisodeId() { return traumaEpisodeId; }
    public void setTraumaEpisodeId(UUID v) { this.traumaEpisodeId = v; }
    public UUID getUnitId() { return unitId; }
    public void setUnitId(UUID v) { this.unitId = v; }
    public String getState() { return state; }
    public void setState(String v) { this.state = v; }
    public String getDispatchPriority() { return dispatchPriority; }
    public void setDispatchPriority(String v) { this.dispatchPriority = v; }
    public UUID getDestinationFacilityId() { return destinationFacilityId; }
    public void setDestinationFacilityId(UUID v) { this.destinationFacilityId = v; }
    public Integer getDestinationEtaSeconds() { return destinationEtaSeconds; }
    public void setDestinationEtaSeconds(Integer v) { this.destinationEtaSeconds = v; }
    public String getPctEncounterRef() { return pctEncounterRef; }
    public void setPctEncounterRef(String v) { this.pctEncounterRef = v; }
    public String getStanddownReason() { return standdownReason; }
    public void setStanddownReason(String v) { this.standdownReason = v; }
    public OffsetDateTime getDispatchedAt() { return dispatchedAt; }
    public void setDispatchedAt(OffsetDateTime v) { this.dispatchedAt = v; }
    public OffsetDateTime getOnSceneAt() { return onSceneAt; }
    public void setOnSceneAt(OffsetDateTime v) { this.onSceneAt = v; }
    public OffsetDateTime getDepartedSceneAt() { return departedSceneAt; }
    public void setDepartedSceneAt(OffsetDateTime v) { this.departedSceneAt = v; }
    public OffsetDateTime getArrivedFacilityAt() { return arrivedFacilityAt; }
    public void setArrivedFacilityAt(OffsetDateTime v) { this.arrivedFacilityAt = v; }
    public OffsetDateTime getHandoverAt() { return handoverAt; }
    public void setHandoverAt(OffsetDateTime v) { this.handoverAt = v; }
    public OffsetDateTime getClearedAt() { return clearedAt; }
    public void setClearedAt(OffsetDateTime v) { this.clearedAt = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
