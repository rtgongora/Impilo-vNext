package zw.gov.mohcc.impilo.experience.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "encounters")
public class Encounter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "shift_id")
    private UUID shiftId;

    @Column(name = "encounter_type")
    private String encounterType;

    private String status;

    @Column(name = "chief_complaint")
    private String chiefComplaint;

    private String diagnosis;

    @Column(columnDefinition = "jsonb")
    private String notes;

    @Column(columnDefinition = "jsonb")
    private String vitals;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Encounter() {}

    @Column(name = "discharge_type")
    private String dischargeType;

    @Column(name = "discharge_diagnosis", columnDefinition = "text")
    private String dischargeDiagnosis;

    @Column(name = "treatment_summary", columnDefinition = "text")
    private String treatmentSummary;

    @Column(name = "follow_up_instructions", columnDefinition = "text")
    private String followUpInstructions;

    @Column(name = "medications_at_discharge", columnDefinition = "text")
    private String medicationsAtDischarge;

    @Column(name = "patient_instructions", columnDefinition = "text")
    private String patientInstructions;

    @Column(name = "discharged_by")
    private String dischargedBy;

    @Column(name = "discharged_at")
    private OffsetDateTime dischargedAt;

    public void close() {
        this.endedAt = OffsetDateTime.now();
        this.status = "COMPLETED";
        this.updatedAt = OffsetDateTime.now();
    }

    public void discharge(String dischargeType, String dischargeDiagnosis,
                           String treatmentSummary, String followUpInstructions,
                           String medicationsAtDischarge, String patientInstructions,
                           String dischargedBy) {
        this.dischargeType = dischargeType;
        this.dischargeDiagnosis = dischargeDiagnosis;
        this.treatmentSummary = treatmentSummary;
        this.followUpInstructions = followUpInstructions;
        this.medicationsAtDischarge = medicationsAtDischarge;
        this.patientInstructions = patientInstructions;
        this.dischargedBy = dischargedBy;
        this.dischargedAt = OffsetDateTime.now();
        this.endedAt = OffsetDateTime.now();
        this.status = "DISCHARGED";
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getFacilityId() { return facilityId; }
    public UUID getPatientId() { return patientId; }
    public UUID getShiftId() { return shiftId; }
    public String getEncounterType() { return encounterType; }
    public String getStatus() { return status; }
    public String getChiefComplaint() { return chiefComplaint; }
    public String getDiagnosis() { return diagnosis; }
    public String getNotes() { return notes; }
    public String getVitals() { return vitals; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getEndedAt() { return endedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public String getDischargeType() { return dischargeType; }
    public String getDischargeDiagnosis() { return dischargeDiagnosis; }
    public String getTreatmentSummary() { return treatmentSummary; }
    public String getFollowUpInstructions() { return followUpInstructions; }
    public String getMedicationsAtDischarge() { return medicationsAtDischarge; }
    public String getPatientInstructions() { return patientInstructions; }
    public String getDischargedBy() { return dischargedBy; }
    public OffsetDateTime getDischargedAt() { return dischargedAt; }
}
