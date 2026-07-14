package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Surgery-specialised discharge summary (Wave 4 §14) — an episode-keyed projection of the case SoR
 * (procedure_episode). Carries the operative procedure summary, wound/drain status, pending-pathology
 * follow-up, rehab plan, sick-leave, referral-back and patient-facing instructions.
 */
@Entity
@Table(name = "procedure_discharge", schema = "inpatient")
public class ProcedureDischargeEntity {

    @Id
    @Column(name = "discharge_id")
    private UUID dischargeId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "episode_id", nullable = false, unique = true)
    private UUID episodeId;

    @Column(name = "subject_cpid")
    private String subjectCpid;

    @Column(name = "encounter_ref")
    private UUID encounterRef;

    @Column(name = "final_diagnosis")
    private String finalDiagnosis;

    @Column(name = "procedure_summary")
    private String procedureSummary;

    @Column(name = "complications")
    private String complications;

    @Column(name = "wound_status")
    private String woundStatus;

    @Column(name = "drain_status")
    private String drainStatus;

    @Column(name = "discharge_medications_json")
    private String dischargeMedicationsJson;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Column(name = "follow_up_service")
    private String followUpService;

    @Column(name = "follow_up_booking_ref")
    private String followUpBookingRef;

    @Column(name = "follow_up_is_teleconsult", nullable = false)
    private boolean followUpIsTeleconsult = false;

    @Column(name = "warning_signs")
    private String warningSigns;

    @Column(name = "rehab_plan")
    private String rehabPlan;

    @Column(name = "pending_pathology_json")
    private String pendingPathologyJson;

    @Column(name = "sick_leave_days")
    private Integer sickLeaveDays;

    @Column(name = "supporting_docs_json")
    private String supportingDocsJson;

    @Column(name = "referral_back_facility_ref")
    private String referralBackFacilityRef;

    @Column(name = "patient_instructions")
    private String patientInstructions;

    @Column(name = "provider_summary")
    private String providerSummary;

    @Column(name = "butano_document_ref")
    private String butanoDocumentRef;

    @Column(name = "status", nullable = false)
    private String status = "DRAFT";

    @Column(name = "authored_by")
    private String authoredBy;

    @Column(name = "completed_by")
    private String completedBy;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (dischargeId == null) dischargeId = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getDischargeId() { return dischargeId; }
    public void setDischargeId(UUID v) { this.dischargeId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID v) { this.episodeId = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { this.subjectCpid = v; }
    public UUID getEncounterRef() { return encounterRef; }
    public void setEncounterRef(UUID v) { this.encounterRef = v; }
    public String getFinalDiagnosis() { return finalDiagnosis; }
    public void setFinalDiagnosis(String v) { this.finalDiagnosis = v; }
    public String getProcedureSummary() { return procedureSummary; }
    public void setProcedureSummary(String v) { this.procedureSummary = v; }
    public String getComplications() { return complications; }
    public void setComplications(String v) { this.complications = v; }
    public String getWoundStatus() { return woundStatus; }
    public void setWoundStatus(String v) { this.woundStatus = v; }
    public String getDrainStatus() { return drainStatus; }
    public void setDrainStatus(String v) { this.drainStatus = v; }
    public String getDischargeMedicationsJson() { return dischargeMedicationsJson; }
    public void setDischargeMedicationsJson(String v) { this.dischargeMedicationsJson = v; }
    public LocalDate getFollowUpDate() { return followUpDate; }
    public void setFollowUpDate(LocalDate v) { this.followUpDate = v; }
    public String getFollowUpService() { return followUpService; }
    public void setFollowUpService(String v) { this.followUpService = v; }
    public String getFollowUpBookingRef() { return followUpBookingRef; }
    public void setFollowUpBookingRef(String v) { this.followUpBookingRef = v; }
    public boolean isFollowUpIsTeleconsult() { return followUpIsTeleconsult; }
    public void setFollowUpIsTeleconsult(boolean v) { this.followUpIsTeleconsult = v; }
    public String getWarningSigns() { return warningSigns; }
    public void setWarningSigns(String v) { this.warningSigns = v; }
    public String getRehabPlan() { return rehabPlan; }
    public void setRehabPlan(String v) { this.rehabPlan = v; }
    public String getPendingPathologyJson() { return pendingPathologyJson; }
    public void setPendingPathologyJson(String v) { this.pendingPathologyJson = v; }
    public Integer getSickLeaveDays() { return sickLeaveDays; }
    public void setSickLeaveDays(Integer v) { this.sickLeaveDays = v; }
    public String getSupportingDocsJson() { return supportingDocsJson; }
    public void setSupportingDocsJson(String v) { this.supportingDocsJson = v; }
    public String getReferralBackFacilityRef() { return referralBackFacilityRef; }
    public void setReferralBackFacilityRef(String v) { this.referralBackFacilityRef = v; }
    public String getPatientInstructions() { return patientInstructions; }
    public void setPatientInstructions(String v) { this.patientInstructions = v; }
    public String getProviderSummary() { return providerSummary; }
    public void setProviderSummary(String v) { this.providerSummary = v; }
    public String getButanoDocumentRef() { return butanoDocumentRef; }
    public void setButanoDocumentRef(String v) { this.butanoDocumentRef = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getAuthoredBy() { return authoredBy; }
    public void setAuthoredBy(String v) { this.authoredBy = v; }
    public String getCompletedBy() { return completedBy; }
    public void setCompletedBy(String v) { this.completedBy = v; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime v) { this.completedAt = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
