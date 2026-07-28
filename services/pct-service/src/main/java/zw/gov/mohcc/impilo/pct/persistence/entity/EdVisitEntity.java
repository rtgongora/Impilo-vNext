package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ed_visit")
public class EdVisitEntity {

    @Id
    @Column(name = "visit_id")
    private UUID visitId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "journey_id", nullable = false, unique = true, length = 26)
    private String journeyId;

    @Column(name = "patient_cpid", nullable = false)
    private String patientCpid;

    @Column(name = "emergency_case_id")
    private UUID emergencyCaseId;

    /** Canonical trauma-episode correlation id (DAIDZAI-minted). Null for non-trauma ED visits. */
    @Column(name = "trauma_episode_id")
    private UUID traumaEpisodeId;

    /** The pct.emergency_episode minted alongside this visit at registration (W15). See V211. */
    @Column(name = "emergency_episode_id")
    private UUID emergencyEpisodeId;

    @Column(name = "arrival_mode", nullable = false)
    private String arrivalMode = "WALK_IN";

    @Column(name = "ambulance_call_sign")
    private String ambulanceCallSign;

    @Column(name = "chief_complaint", columnDefinition = "TEXT")
    private String chiefComplaint;

    @Column(name = "presenting_problem_code")
    private String presentingProblemCode;

    @Column(name = "status", nullable = false)
    private String status = "REGISTERED";

    @Column(name = "zone")
    private String zone;

    @Column(name = "track_number")
    private String trackNumber;

    @Column(name = "assigned_clinician")
    private String assignedClinician;

    @Column(name = "pathway_ref")
    private String pathwayRef;

    @Column(name = "protocol_ref")
    private String protocolRef;

    @Column(name = "pathway_session_id")
    private UUID pathwaySessionId;

    @Column(name = "encounter_id")
    private Long encounterId;

    @Column(name = "current_acuity")
    private Integer currentAcuity;

    @Column(name = "news2_score")
    private Integer news2Score;

    /** ED pre-arrival projection: the latest prehospital ePCR snapshot pushed by DAIDZAI EMS (W3). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pre_arrival_json", columnDefinition = "jsonb")
    private String preArrivalJson;

    @Column(name = "ems_mission_ref")
    private String emsMissionRef;

    @Column(name = "pre_arrival_at")
    private OffsetDateTime preArrivalAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (visitId == null) visitId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getVisitId() { return visitId; }
    public void setVisitId(UUID visitId) { this.visitId = visitId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String journeyId) { this.journeyId = journeyId; }
    public String getPatientCpid() { return patientCpid; }
    public void setPatientCpid(String patientCpid) { this.patientCpid = patientCpid; }
    public UUID getEmergencyCaseId() { return emergencyCaseId; }
    public void setEmergencyCaseId(UUID emergencyCaseId) { this.emergencyCaseId = emergencyCaseId; }
    public UUID getTraumaEpisodeId() { return traumaEpisodeId; }
    public void setTraumaEpisodeId(UUID traumaEpisodeId) { this.traumaEpisodeId = traumaEpisodeId; }
    public UUID getEmergencyEpisodeId() { return emergencyEpisodeId; }
    public void setEmergencyEpisodeId(UUID emergencyEpisodeId) { this.emergencyEpisodeId = emergencyEpisodeId; }
    public String getArrivalMode() { return arrivalMode; }
    public void setArrivalMode(String arrivalMode) { this.arrivalMode = arrivalMode; }
    public String getAmbulanceCallSign() { return ambulanceCallSign; }
    public void setAmbulanceCallSign(String ambulanceCallSign) { this.ambulanceCallSign = ambulanceCallSign; }
    public String getChiefComplaint() { return chiefComplaint; }
    public void setChiefComplaint(String chiefComplaint) { this.chiefComplaint = chiefComplaint; }
    public String getPresentingProblemCode() { return presentingProblemCode; }
    public void setPresentingProblemCode(String presentingProblemCode) { this.presentingProblemCode = presentingProblemCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }
    public String getTrackNumber() { return trackNumber; }
    public void setTrackNumber(String trackNumber) { this.trackNumber = trackNumber; }
    public String getAssignedClinician() { return assignedClinician; }
    public void setAssignedClinician(String assignedClinician) { this.assignedClinician = assignedClinician; }
    public String getPathwayRef() { return pathwayRef; }
    public void setPathwayRef(String pathwayRef) { this.pathwayRef = pathwayRef; }
    public String getProtocolRef() { return protocolRef; }
    public void setProtocolRef(String protocolRef) { this.protocolRef = protocolRef; }
    public UUID getPathwaySessionId() { return pathwaySessionId; }
    public void setPathwaySessionId(UUID pathwaySessionId) { this.pathwaySessionId = pathwaySessionId; }
    public Long getEncounterId() { return encounterId; }
    public void setEncounterId(Long encounterId) { this.encounterId = encounterId; }
    public Integer getCurrentAcuity() { return currentAcuity; }
    public void setCurrentAcuity(Integer currentAcuity) { this.currentAcuity = currentAcuity; }
    public Integer getNews2Score() { return news2Score; }
    public void setNews2Score(Integer news2Score) { this.news2Score = news2Score; }
    public String getPreArrivalJson() { return preArrivalJson; }
    public void setPreArrivalJson(String preArrivalJson) { this.preArrivalJson = preArrivalJson; }
    public String getEmsMissionRef() { return emsMissionRef; }
    public void setEmsMissionRef(String emsMissionRef) { this.emsMissionRef = emsMissionRef; }
    public OffsetDateTime getPreArrivalAt() { return preArrivalAt; }
    public void setPreArrivalAt(OffsetDateTime preArrivalAt) { this.preArrivalAt = preArrivalAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
