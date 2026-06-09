package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "shift_handover_item", schema = "inpatient")
public class ShiftHandoverItemEntity {

    @Id
    @Column(name = "item_id")
    private UUID itemId;

    @Column(name = "handover_id", nullable = false)
    private UUID handoverId;

    @Column(name = "admission_ref", nullable = false)
    private UUID admissionRef;

    @Column(name = "subject_cpid", nullable = false)
    private String subjectCpid;

    @Column(name = "acuity", nullable = false)
    private String acuity = "STABLE";

    @Column(name = "diagnosis")
    private String diagnosis;

    @Column(name = "vital_status")
    private String vitalStatus = "STABLE";

    @Column(name = "key_meds_json")
    private String keyMedsJson;

    @Column(name = "pending_tasks_json")
    private String pendingTasksJson;

    @Column(name = "patient_notes")
    private String patientNotes;

    @PrePersist
    void onCreate() {
        if (itemId == null) itemId = UUID.randomUUID();
    }

    public UUID getItemId() { return itemId; }
    public void setItemId(UUID itemId) { this.itemId = itemId; }
    public UUID getHandoverId() { return handoverId; }
    public void setHandoverId(UUID handoverId) { this.handoverId = handoverId; }
    public UUID getAdmissionRef() { return admissionRef; }
    public void setAdmissionRef(UUID admissionRef) { this.admissionRef = admissionRef; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String subjectCpid) { this.subjectCpid = subjectCpid; }
    public String getAcuity() { return acuity; }
    public void setAcuity(String acuity) { this.acuity = acuity; }
    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }
    public String getVitalStatus() { return vitalStatus; }
    public void setVitalStatus(String vitalStatus) { this.vitalStatus = vitalStatus; }
    public String getKeyMedsJson() { return keyMedsJson; }
    public void setKeyMedsJson(String keyMedsJson) { this.keyMedsJson = keyMedsJson; }
    public String getPendingTasksJson() { return pendingTasksJson; }
    public void setPendingTasksJson(String pendingTasksJson) { this.pendingTasksJson = pendingTasksJson; }
    public String getPatientNotes() { return patientNotes; }
    public void setPatientNotes(String patientNotes) { this.patientNotes = patientNotes; }
}
