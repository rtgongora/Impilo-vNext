package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The inpatient discharge summary — native rich domain object (med reconciliation, discharge meds,
 * follow-up, referrals). Carries a FHIR Composition projection that is emitted toward Butano/SHR on
 * finalisation. One summary per encounter.
 */
@Entity
@Table(name = "discharge_summary", schema = "inpatient")
public class DischargeSummaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "summary_id")
    private UUID summaryId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "encounter_id", nullable = false)
    private UUID encounterId;

    @Column(name = "admission_ref")
    private UUID admissionRef;

    @Column(name = "subject_cpid", nullable = false)
    private String subjectCpid;

    @Column(name = "status", nullable = false)
    private String status = "DRAFT";

    @Column(name = "discharge_diagnosis")
    private String dischargeDiagnosis;

    @Column(name = "discharge_disposition")
    private String dischargeDisposition;

    @Column(name = "hospital_course")
    private String hospitalCourse;

    @Column(name = "medication_reconciliation_json")
    private String medicationReconciliationJson;

    @Column(name = "discharge_medications_json")
    private String dischargeMedicationsJson;

    @Column(name = "follow_up_json")
    private String followUpJson;

    @Column(name = "referrals_json")
    private String referralsJson;

    @Column(name = "fhir_composition_json")
    private String fhirCompositionJson;

    @Column(name = "finalised_by")
    private String finalisedBy;

    @Column(name = "finalised_at")
    private OffsetDateTime finalisedAt;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getSummaryId() { return summaryId; }
    public void setSummaryId(UUID v) { this.summaryId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID v) { this.facilityId = v; }
    public UUID getEncounterId() { return encounterId; }
    public void setEncounterId(UUID v) { this.encounterId = v; }
    public UUID getAdmissionRef() { return admissionRef; }
    public void setAdmissionRef(UUID v) { this.admissionRef = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { this.subjectCpid = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getDischargeDiagnosis() { return dischargeDiagnosis; }
    public void setDischargeDiagnosis(String v) { this.dischargeDiagnosis = v; }
    public String getDischargeDisposition() { return dischargeDisposition; }
    public void setDischargeDisposition(String v) { this.dischargeDisposition = v; }
    public String getHospitalCourse() { return hospitalCourse; }
    public void setHospitalCourse(String v) { this.hospitalCourse = v; }
    public String getMedicationReconciliationJson() { return medicationReconciliationJson; }
    public void setMedicationReconciliationJson(String v) { this.medicationReconciliationJson = v; }
    public String getDischargeMedicationsJson() { return dischargeMedicationsJson; }
    public void setDischargeMedicationsJson(String v) { this.dischargeMedicationsJson = v; }
    public String getFollowUpJson() { return followUpJson; }
    public void setFollowUpJson(String v) { this.followUpJson = v; }
    public String getReferralsJson() { return referralsJson; }
    public void setReferralsJson(String v) { this.referralsJson = v; }
    public String getFhirCompositionJson() { return fhirCompositionJson; }
    public void setFhirCompositionJson(String v) { this.fhirCompositionJson = v; }
    public String getFinalisedBy() { return finalisedBy; }
    public void setFinalisedBy(String v) { this.finalisedBy = v; }
    public OffsetDateTime getFinalisedAt() { return finalisedAt; }
    public void setFinalisedAt(OffsetDateTime v) { this.finalisedAt = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
