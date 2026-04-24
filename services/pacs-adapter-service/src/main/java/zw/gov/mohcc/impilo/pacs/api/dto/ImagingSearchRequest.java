package zw.gov.mohcc.impilo.pacs.api.dto;

/**
 * POST body for governed imaging study search (all fields optional; at least one filter recommended).
 */
public class ImagingSearchRequest {

    private String patientCpid;
    private String studyUid;
    private String accessionNumber;
    private String modality;
    private String facilityId;
    private String reportStatus;
    /** ISO-8601 date-time lower bound (inclusive), compared to studyDate */
    private String studyDateFrom;
    /** ISO-8601 date-time upper bound (inclusive) */
    private String studyDateTo;

    public String getPatientCpid() {
        return patientCpid;
    }

    public void setPatientCpid(String patientCpid) {
        this.patientCpid = patientCpid;
    }

    public String getStudyUid() {
        return studyUid;
    }

    public void setStudyUid(String studyUid) {
        this.studyUid = studyUid;
    }

    public String getAccessionNumber() {
        return accessionNumber;
    }

    public void setAccessionNumber(String accessionNumber) {
        this.accessionNumber = accessionNumber;
    }

    public String getModality() {
        return modality;
    }

    public void setModality(String modality) {
        this.modality = modality;
    }

    public String getFacilityId() {
        return facilityId;
    }

    public void setFacilityId(String facilityId) {
        this.facilityId = facilityId;
    }

    public String getReportStatus() {
        return reportStatus;
    }

    public void setReportStatus(String reportStatus) {
        this.reportStatus = reportStatus;
    }

    public String getStudyDateFrom() {
        return studyDateFrom;
    }

    public void setStudyDateFrom(String studyDateFrom) {
        this.studyDateFrom = studyDateFrom;
    }

    public String getStudyDateTo() {
        return studyDateTo;
    }

    public void setStudyDateTo(String studyDateTo) {
        this.studyDateTo = studyDateTo;
    }
}
