package zw.gov.mohcc.impilo.datawarehouse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dwh_gold_lab")
public class GoldLabEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "lab_result_id", nullable = false, length = 255)
    private String labResultId;

    @Column(name = "patient_id", nullable = false, length = 255)
    private String patientId;

    @Column(name = "encounter_id", length = 255)
    private String encounterId;

    @Column(name = "facility_id", length = 255)
    private String facilityId;

    @Column(name = "test_code", length = 128)
    private String testCode;

    @Column(name = "test_name", length = 512)
    private String testName;

    @Column(name = "result_value", length = 512)
    private String resultValue;

    @Column(name = "result_unit", length = 64)
    private String resultUnit;

    @Column(name = "reference_range", length = 255)
    private String referenceRange;

    @Column(name = "result_status", length = 64)
    private String resultStatus;

    @Column(name = "collected_date")
    private OffsetDateTime collectedDate;

    @Column(name = "reported_date")
    private OffsetDateTime reportedDate;

    @Column(name = "source_event_id", nullable = false, length = 255)
    private String sourceEventId;

    @Column(name = "source_event_type", nullable = false, length = 128)
    private String sourceEventType;

    @Column(name = "materialized_at", nullable = false)
    private OffsetDateTime materializedAt = OffsetDateTime.now();

    public GoldLabEntity() {}

    // Getters
    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getLabResultId() { return labResultId; }
    public String getPatientId() { return patientId; }
    public String getEncounterId() { return encounterId; }
    public String getFacilityId() { return facilityId; }
    public String getTestCode() { return testCode; }
    public String getTestName() { return testName; }
    public String getResultValue() { return resultValue; }
    public String getResultUnit() { return resultUnit; }
    public String getReferenceRange() { return referenceRange; }
    public String getResultStatus() { return resultStatus; }
    public OffsetDateTime getCollectedDate() { return collectedDate; }
    public OffsetDateTime getReportedDate() { return reportedDate; }
    public String getSourceEventId() { return sourceEventId; }
    public String getSourceEventType() { return sourceEventType; }
    public OffsetDateTime getMaterializedAt() { return materializedAt; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public void setLabResultId(String labResultId) { this.labResultId = labResultId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }
    public void setEncounterId(String encounterId) { this.encounterId = encounterId; }
    public void setFacilityId(String facilityId) { this.facilityId = facilityId; }
    public void setTestCode(String testCode) { this.testCode = testCode; }
    public void setTestName(String testName) { this.testName = testName; }
    public void setResultValue(String resultValue) { this.resultValue = resultValue; }
    public void setResultUnit(String resultUnit) { this.resultUnit = resultUnit; }
    public void setReferenceRange(String referenceRange) { this.referenceRange = referenceRange; }
    public void setResultStatus(String resultStatus) { this.resultStatus = resultStatus; }
    public void setCollectedDate(OffsetDateTime collectedDate) { this.collectedDate = collectedDate; }
    public void setReportedDate(OffsetDateTime reportedDate) { this.reportedDate = reportedDate; }
    public void setSourceEventId(String sourceEventId) { this.sourceEventId = sourceEventId; }
    public void setSourceEventType(String sourceEventType) { this.sourceEventType = sourceEventType; }
    public void setMaterializedAt(OffsetDateTime materializedAt) { this.materializedAt = materializedAt; }
}
