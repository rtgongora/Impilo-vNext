package zw.gov.mohcc.impilo.pacs.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Request body for registering a new imaging study.
 */
public class CreateImagingStudyRequest {

    @NotNull
    private UUID tenantId;

    @NotBlank
    private String patientCpid;

    @NotBlank
    private String studyUid;

    @NotBlank
    private String modality;

    private String description;

    @NotNull
    private OffsetDateTime studyDate;

    private String metadata;

    /** Optional OROS order correlation at ingest time (when already known). */
    private String orosOrderId;

    /** Optional accession / local identifier for reconciliation. */
    private String accessionNumber;

    /** Optional encounter anchor (EHR encounter id or URI). */
    private String encounterRef;

    /** Optional facility / site (Tuso site id or URI). */
    private String facilityId;

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

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public String getOrosOrderId() { return orosOrderId; }
    public void setOrosOrderId(String orosOrderId) { this.orosOrderId = orosOrderId; }

    public String getAccessionNumber() { return accessionNumber; }
    public void setAccessionNumber(String accessionNumber) { this.accessionNumber = accessionNumber; }

    public String getEncounterRef() { return encounterRef; }
    public void setEncounterRef(String encounterRef) { this.encounterRef = encounterRef; }

    public String getFacilityId() { return facilityId; }
    public void setFacilityId(String facilityId) { this.facilityId = facilityId; }
}
