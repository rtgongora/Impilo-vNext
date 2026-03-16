package zw.gov.mohcc.impilo.experience.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "conditions")
public class Condition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "condition_name")
    private String conditionName;

    @Column(name = "icd_code")
    private String icdCode;

    private String category;

    @Column(name = "clinical_status")
    private String clinicalStatus;

    private String severity;

    @Column(name = "onset_date")
    private LocalDate onsetDate;

    @Column(name = "abatement_date")
    private LocalDate abatementDate;

    @Column(name = "recorded_by")
    private String recordedBy;

    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Condition() {}

    public void resolve() {
        this.clinicalStatus = "RESOLVED";
        this.abatementDate = LocalDate.now();
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getPatientId() { return patientId; }
    public UUID getEncounterId() { return encounterId; }
    public String getConditionName() { return conditionName; }
    public String getIcdCode() { return icdCode; }
    public String getCategory() { return category; }
    public String getClinicalStatus() { return clinicalStatus; }
    public String getSeverity() { return severity; }
    public LocalDate getOnsetDate() { return onsetDate; }
    public LocalDate getAbatementDate() { return abatementDate; }
    public String getRecordedBy() { return recordedBy; }
    public String getNotes() { return notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
