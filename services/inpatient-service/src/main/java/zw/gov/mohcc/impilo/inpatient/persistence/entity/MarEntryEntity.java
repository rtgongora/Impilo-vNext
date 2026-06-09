package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mar_entry", schema = "inpatient")
public class MarEntryEntity {

    @Id
    @Column(name = "mar_id")
    private UUID marId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_cpid", nullable = false)
    private String subjectCpid;

    @Column(name = "admission_ref")
    private UUID admissionRef;

    @Column(name = "prescription_id")
    private String prescriptionId;

    @Column(name = "drug_name", nullable = false)
    private String drugName;

    @Column(name = "dose")
    private String dose;

    @Column(name = "route")
    private String route;

    @Column(name = "time_due")
    private String timeDue;

    @Column(name = "time_given")
    private String timeGiven;

    @Column(name = "status", nullable = false)
    private String status = "SCHEDULED";

    @Column(name = "administered_by")
    private String administeredBy;

    @Column(name = "notes")
    private String notes;

    @Column(name = "recorded_at")
    private OffsetDateTime recordedAt;

    @PrePersist
    void onCreate() {
        if (marId == null) marId = UUID.randomUUID();
        if (recordedAt == null) recordedAt = OffsetDateTime.now();
    }

    public UUID getMarId() { return marId; }
    public void setMarId(UUID marId) { this.marId = marId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String subjectCpid) { this.subjectCpid = subjectCpid; }
    public UUID getAdmissionRef() { return admissionRef; }
    public void setAdmissionRef(UUID admissionRef) { this.admissionRef = admissionRef; }
    public String getPrescriptionId() { return prescriptionId; }
    public void setPrescriptionId(String prescriptionId) { this.prescriptionId = prescriptionId; }
    public String getDrugName() { return drugName; }
    public void setDrugName(String drugName) { this.drugName = drugName; }
    public String getDose() { return dose; }
    public void setDose(String dose) { this.dose = dose; }
    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }
    public String getTimeDue() { return timeDue; }
    public void setTimeDue(String timeDue) { this.timeDue = timeDue; }
    public String getTimeGiven() { return timeGiven; }
    public void setTimeGiven(String timeGiven) { this.timeGiven = timeGiven; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAdministeredBy() { return administeredBy; }
    public void setAdministeredBy(String administeredBy) { this.administeredBy = administeredBy; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime recordedAt) { this.recordedAt = recordedAt; }
}
