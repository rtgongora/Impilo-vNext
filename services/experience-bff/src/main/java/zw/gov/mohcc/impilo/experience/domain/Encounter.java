package zw.gov.mohcc.impilo.experience.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Encounter domain entity with disposition-aware terminal status.
 *
 * <p>Encounter lifecycle:</p>
 * <pre>
 *   IN_PROGRESS → COMPLETED (via close)
 *   IN_PROGRESS → DISCHARGED | ADMITTED | TRANSFERRED | REFERRED | DECEASED | LAMA (via discharge)
 * </pre>
 *
 * <p>The terminal status is determined by the discharge_type, ensuring
 * the encounter status accurately reflects the visit outcome rather
 * than collapsing all dispositions into a single "DISCHARGED" state.</p>
 */
@Entity
@Table(name = "encounters")
public class Encounter {

    /**
     * Maps discharge_type to the appropriate terminal encounter status.
     * Values not in this map default to "DISCHARGED".
     */
    private static final Map<String, String> DISPOSITION_STATUS_MAP = Map.of(
            "CLINICAL", "DISCHARGED",
            "ADMIT", "ADMITTED",
            "TRANSFER", "TRANSFERRED",
            "REFER", "REFERRED",
            "DEATH", "DECEASED",
            "AMA", "LAMA"
    );

    private static final Set<String> ACTIVE_STATUSES = Set.of("IN_PROGRESS", "ACTIVE");

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

    /**
     * Close the encounter without a specific disposition (simple completion).
     * Sets status to COMPLETED.
     *
     * @throws IllegalStateException if encounter is not in an active status
     */
    public void close() {
        guardActive("close");
        this.endedAt = OffsetDateTime.now();
        this.status = "COMPLETED";
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * Complete the encounter with a specific disposition type.
     * The terminal status is determined by the discharge_type:
     * <ul>
     *   <li>CLINICAL → DISCHARGED</li>
     *   <li>ADMIT → ADMITTED</li>
     *   <li>TRANSFER → TRANSFERRED</li>
     *   <li>REFER → REFERRED</li>
     *   <li>DEATH → DECEASED</li>
     *   <li>AMA → LAMA</li>
     * </ul>
     *
     * @throws IllegalStateException if encounter is not in an active status
     */
    public void discharge(String dischargeType, String dischargeDiagnosis,
                           String treatmentSummary, String followUpInstructions,
                           String medicationsAtDischarge, String patientInstructions,
                           String dischargedBy) {
        guardActive("discharge");
        this.dischargeType = dischargeType;
        this.dischargeDiagnosis = dischargeDiagnosis;
        this.treatmentSummary = treatmentSummary;
        this.followUpInstructions = followUpInstructions;
        this.medicationsAtDischarge = medicationsAtDischarge;
        this.patientInstructions = patientInstructions;
        this.dischargedBy = dischargedBy;
        this.dischargedAt = OffsetDateTime.now();
        this.endedAt = OffsetDateTime.now();
        this.status = resolveTerminalStatus(dischargeType);
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * Check whether this encounter is in an active (completable) state.
     */
    public boolean isActive() {
        return ACTIVE_STATUSES.contains(this.status);
    }

    /**
     * Resolve the terminal encounter status from the discharge type.
     */
    public static String resolveTerminalStatus(String dischargeType) {
        if (dischargeType == null) return "DISCHARGED";
        return DISPOSITION_STATUS_MAP.getOrDefault(dischargeType.toUpperCase(), "DISCHARGED");
    }

    private void guardActive(String action) {
        if (!isActive()) {
            throw new IllegalStateException(
                    "Cannot " + action + " encounter " + id + ": current status is " + status
                    + ", must be one of " + ACTIVE_STATUSES);
        }
    }

    // ── Getters ──────────────────────────────────────────────────

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
