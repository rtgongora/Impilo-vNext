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

    @Column(name = "oros_order_id", length = 26)
    private String orosOrderId;

    @Column(name = "accession_number", length = 128)
    private String accessionNumber;

    @Column(name = "encounter_ref", length = 128)
    private String encounterRef;

    @Column(name = "facility_id", length = 128)
    private String facilityId;

    @Column(name = "archive_status", length = 32)
    private String archiveStatus = "UNKNOWN";

    @Column(name = "report_status", length = 64)
    private String reportStatus;

    @Column(name = "source_type", length = 32)
    private String sourceType = "DICOM";

    @Column(name = "body_part_examined", length = 500)
    private String bodyPartExamined;

    @Column(name = "modality_system", length = 255)
    private String modalitySystem;

    @Column(name = "body_part_system", length = 255)
    private String bodyPartSystem;

    @Column(name = "body_part_code", length = 100)
    private String bodyPartCode;

    @Column(name = "body_part_display", length = 500)
    private String bodyPartDisplay;

    @Column(name = "procedure_system", length = 255)
    private String procedureSystem;

    @Column(name = "procedure_code", length = 100)
    private String procedureCode;

    @Column(name = "procedure_display", length = 500)
    private String procedureDisplay;

    @Column(name = "reason_system", length = 255)
    private String reasonSystem;

    @Column(name = "reason_code", length = 100)
    private String reasonCode;

    @Column(name = "reason_display", length = 500)
    private String reasonDisplay;

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

    public String getOrosOrderId() { return orosOrderId; }
    public void setOrosOrderId(String orosOrderId) { this.orosOrderId = orosOrderId; }

    public String getAccessionNumber() { return accessionNumber; }
    public void setAccessionNumber(String accessionNumber) { this.accessionNumber = accessionNumber; }

    public String getEncounterRef() { return encounterRef; }
    public void setEncounterRef(String encounterRef) { this.encounterRef = encounterRef; }

    public String getFacilityId() { return facilityId; }
    public void setFacilityId(String facilityId) { this.facilityId = facilityId; }

    public String getArchiveStatus() { return archiveStatus; }
    public void setArchiveStatus(String archiveStatus) { this.archiveStatus = archiveStatus; }

    public String getReportStatus() { return reportStatus; }
    public void setReportStatus(String reportStatus) { this.reportStatus = reportStatus; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getBodyPartExamined() { return bodyPartExamined; }
    public void setBodyPartExamined(String bodyPartExamined) { this.bodyPartExamined = bodyPartExamined; }

    public String getModalitySystem() { return modalitySystem; }
    public void setModalitySystem(String modalitySystem) { this.modalitySystem = modalitySystem; }

    public String getBodyPartSystem() { return bodyPartSystem; }
    public void setBodyPartSystem(String bodyPartSystem) { this.bodyPartSystem = bodyPartSystem; }

    public String getBodyPartCode() { return bodyPartCode; }
    public void setBodyPartCode(String bodyPartCode) { this.bodyPartCode = bodyPartCode; }

    public String getBodyPartDisplay() { return bodyPartDisplay; }
    public void setBodyPartDisplay(String bodyPartDisplay) { this.bodyPartDisplay = bodyPartDisplay; }

    public String getProcedureSystem() { return procedureSystem; }
    public void setProcedureSystem(String procedureSystem) { this.procedureSystem = procedureSystem; }

    public String getProcedureCode() { return procedureCode; }
    public void setProcedureCode(String procedureCode) { this.procedureCode = procedureCode; }

    public String getProcedureDisplay() { return procedureDisplay; }
    public void setProcedureDisplay(String procedureDisplay) { this.procedureDisplay = procedureDisplay; }

    public String getReasonSystem() { return reasonSystem; }
    public void setReasonSystem(String reasonSystem) { this.reasonSystem = reasonSystem; }

    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }

    public String getReasonDisplay() { return reasonDisplay; }
    public void setReasonDisplay(String reasonDisplay) { this.reasonDisplay = reasonDisplay; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
