package zw.gov.mohcc.impilo.pacs.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity mapping to the {@code pacs.imaging_study} table.
 */
@Entity
@Table(name = "imaging_study", schema = "pacs")
public class ImagingStudyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_cpid", nullable = false, length = 100)
    private String patientCpid;

    @Column(name = "study_uid", nullable = false, unique = true, length = 255)
    private String studyUid;

    @Column(name = "modality", nullable = false, length = 20)
    private String modality;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "study_date", nullable = false)
    private OffsetDateTime studyDate;

    @Column(name = "status", length = 20)
    private String status = "RECEIVED";

    @Column(name = "orthanc_id", length = 255)
    private String orthancId;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getPatientCpid() { return patientCpid; }
    public void setPatientCpid(String patientCpid) { this.patientCpid = patientCpid; }

    public String getStudyUid() { return studyUid; }
    public void setStudyUid(String studyUid) { this.studyUid = studyUid; }

    public String getModality() { return modality; }
    public void setModality(String modality) { this.modality = modality; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public OffsetDateTime getStudyDate() { return studyDate; }
    public void setStudyDate(OffsetDateTime studyDate) { this.studyDate = studyDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOrthancId() { return orthancId; }
    public void setOrthancId(String orthancId) { this.orthancId = orthancId; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
