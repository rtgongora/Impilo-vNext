package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity mapping to the {@code inpatient.admission} table.
 */
@Entity
@Table(name = "admission", schema = "inpatient")
public class AdmissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "admission_ref", nullable = false, unique = true)
    private UUID admissionRef;

    @Column(name = "encounter_id", nullable = false)
    private UUID encounterId;

    @Column(name = "subject_cpid", nullable = false, length = 64)
    private String subjectCpid;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "ward_id")
    private UUID wardId;

    @Column(name = "bed_id")
    private UUID bedId;

    @Column(name = "status", length = 30)
    private String status = "ADMITTED";

    @Column(name = "admitted_at")
    private OffsetDateTime admittedAt;

    @Column(name = "discharged_at")
    private OffsetDateTime dischargedAt;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (admittedAt == null) {
            admittedAt = OffsetDateTime.now();
        }
        if (admissionRef == null) {
            admissionRef = UUID.randomUUID();
        }
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getAdmissionRef() { return admissionRef; }
    public void setAdmissionRef(UUID admissionRef) { this.admissionRef = admissionRef; }

    public UUID getEncounterId() { return encounterId; }
    public void setEncounterId(UUID encounterId) { this.encounterId = encounterId; }

    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String subjectCpid) { this.subjectCpid = subjectCpid; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public UUID getWardId() { return wardId; }
    public void setWardId(UUID wardId) { this.wardId = wardId; }

    public UUID getBedId() { return bedId; }
    public void setBedId(UUID bedId) { this.bedId = bedId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getAdmittedAt() { return admittedAt; }
    public void setAdmittedAt(OffsetDateTime admittedAt) { this.admittedAt = admittedAt; }

    public OffsetDateTime getDischargedAt() { return dischargedAt; }
    public void setDischargedAt(OffsetDateTime dischargedAt) { this.dischargedAt = dischargedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
