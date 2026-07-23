package zw.gov.mohcc.impilo.oros.persistence.entity;

import jakarta.persistence.*;
import zw.gov.mohcc.impilo.oros.domain.PrescriptionStatus;
import zw.gov.mohcc.impilo.oros.domain.RequestSource;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The prescription aggregate parent (OF-B2, Vol II §9.2): the clinical authorisation.
 * PHARMACY orders remain the dispense episodes. {@code repeatsRemaining} is the
 * server-side counter — the ONLY decrement path is an atomic claim (§13.2.3).
 */
@Entity
@Table(name = "oros_prescriptions")
public class PrescriptionEntity {

    @Id
    @Column(name = "prescription_id", nullable = false, length = 26)
    private String prescriptionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "patient_cpid", nullable = false, length = 64)
    private String patientCpid;

    @Column(name = "prescriber_id", nullable = false, length = 128)
    private String prescriberId;

    @Column(name = "prescriber_name", length = 255)
    private String prescriberName;

    @Column(name = "encounter_ref", length = 255)
    private String encounterRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_source", nullable = false, length = 32)
    private RequestSource requestSource = RequestSource.INTERNAL;

    @Column(name = "source_ref", length = 128)
    private String sourceRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PrescriptionStatus status = PrescriptionStatus.DRAFT;

    @Column(name = "repeats_ceiling", nullable = false)
    private int repeatsCeiling = 1;

    @Column(name = "repeats_remaining", nullable = false)
    private int repeatsRemaining = 1;

    @Column(name = "validity_start")
    private LocalDate validityStart;

    @Column(name = "validity_end")
    private LocalDate validityEnd;

    @Column(name = "controlled", nullable = false)
    private boolean controlled;

    @Column(name = "substitution_permitted", nullable = false)
    private boolean substitutionPermitted = true;

    @Column(name = "indication", length = 512)
    private String indication;

    @Column(name = "current_version_id", length = 26)
    private String currentVersionId;

    @Column(name = "lifecycle_reason", length = 512)
    private String lifecycleReason;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public String getPrescriptionId() { return prescriptionId; }
    public void setPrescriptionId(String prescriptionId) { this.prescriptionId = prescriptionId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public String getPatientCpid() { return patientCpid; }
    public void setPatientCpid(String patientCpid) { this.patientCpid = patientCpid; }
    public String getPrescriberId() { return prescriberId; }
    public void setPrescriberId(String prescriberId) { this.prescriberId = prescriberId; }
    public String getPrescriberName() { return prescriberName; }
    public void setPrescriberName(String prescriberName) { this.prescriberName = prescriberName; }
    public String getEncounterRef() { return encounterRef; }
    public void setEncounterRef(String encounterRef) { this.encounterRef = encounterRef; }
    public RequestSource getRequestSource() { return requestSource; }
    public void setRequestSource(RequestSource requestSource) { this.requestSource = requestSource; }
    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }
    public PrescriptionStatus getStatus() { return status; }
    public void setStatus(PrescriptionStatus status) { this.status = status; }
    public int getRepeatsCeiling() { return repeatsCeiling; }
    public void setRepeatsCeiling(int repeatsCeiling) { this.repeatsCeiling = repeatsCeiling; }
    public int getRepeatsRemaining() { return repeatsRemaining; }
    public void setRepeatsRemaining(int repeatsRemaining) { this.repeatsRemaining = repeatsRemaining; }
    public LocalDate getValidityStart() { return validityStart; }
    public void setValidityStart(LocalDate validityStart) { this.validityStart = validityStart; }
    public LocalDate getValidityEnd() { return validityEnd; }
    public void setValidityEnd(LocalDate validityEnd) { this.validityEnd = validityEnd; }
    public boolean isControlled() { return controlled; }
    public void setControlled(boolean controlled) { this.controlled = controlled; }
    public boolean isSubstitutionPermitted() { return substitutionPermitted; }
    public void setSubstitutionPermitted(boolean substitutionPermitted) { this.substitutionPermitted = substitutionPermitted; }
    public String getIndication() { return indication; }
    public void setIndication(String indication) { this.indication = indication; }
    public String getCurrentVersionId() { return currentVersionId; }
    public void setCurrentVersionId(String currentVersionId) { this.currentVersionId = currentVersionId; }
    public String getLifecycleReason() { return lifecycleReason; }
    public void setLifecycleReason(String lifecycleReason) { this.lifecycleReason = lifecycleReason; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
