package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "discharge_clearance", schema = "inpatient")
public class DischargeClearanceEntity {

    @Id
    @Column(name = "clearance_id")
    private UUID clearanceId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_cpid")
    private String subjectCpid;

    @Column(name = "encounter_id", nullable = false)
    private UUID encounterId;

    @Column(name = "admission_ref")
    private UUID admissionRef;

    @Column(name = "clearance_type", nullable = false)
    private String clearanceType;

    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "cleared_by")
    private String clearedBy;

    @Column(name = "cleared_at")
    private OffsetDateTime clearedAt;

    @Column(name = "waived", nullable = false)
    private boolean waived;

    @Column(name = "waived_reason")
    private String waivedReason;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (clearanceId == null) clearanceId = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getClearanceId() { return clearanceId; }
    public void setClearanceId(UUID clearanceId) { this.clearanceId = clearanceId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String subjectCpid) { this.subjectCpid = subjectCpid; }
    public UUID getEncounterId() { return encounterId; }
    public void setEncounterId(UUID encounterId) { this.encounterId = encounterId; }
    public UUID getAdmissionRef() { return admissionRef; }
    public void setAdmissionRef(UUID admissionRef) { this.admissionRef = admissionRef; }
    public String getClearanceType() { return clearanceType; }
    public void setClearanceType(String clearanceType) { this.clearanceType = clearanceType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getClearedBy() { return clearedBy; }
    public void setClearedBy(String clearedBy) { this.clearedBy = clearedBy; }
    public OffsetDateTime getClearedAt() { return clearedAt; }
    public void setClearedAt(OffsetDateTime clearedAt) { this.clearedAt = clearedAt; }
    public boolean isWaived() { return waived; }
    public void setWaived(boolean waived) { this.waived = waived; }
    public String getWaivedReason() { return waivedReason; }
    public void setWaivedReason(String waivedReason) { this.waivedReason = waivedReason; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
