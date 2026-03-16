package zw.gov.mohcc.impilo.experience.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "lab_orders")
public class LabOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "order_number")
    private String orderNumber;

    @Column(name = "test_name")
    private String testName;

    @Column(name = "test_code")
    private String testCode;

    private String category;

    private String priority;

    private String status;

    @Column(name = "clinical_notes")
    private String clinicalNotes;

    @Column(name = "ordered_by")
    private String orderedBy;

    @Column(name = "ordered_by_name")
    private String orderedByName;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "collected_at")
    private OffsetDateTime collectedAt;

    @Column(name = "resulted_at")
    private OffsetDateTime resultedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected LabOrder() {}

    public void collect() {
        this.status = "COLLECTED";
        this.collectedAt = OffsetDateTime.now();
    }

    public void result() {
        this.status = "RESULTED";
        this.resultedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getPatientId() { return patientId; }
    public UUID getEncounterId() { return encounterId; }
    public String getOrderNumber() { return orderNumber; }
    public String getTestName() { return testName; }
    public String getTestCode() { return testCode; }
    public String getCategory() { return category; }
    public String getPriority() { return priority; }
    public String getStatus() { return status; }
    public String getClinicalNotes() { return clinicalNotes; }
    public String getOrderedBy() { return orderedBy; }
    public String getOrderedByName() { return orderedByName; }
    public UUID getFacilityId() { return facilityId; }
    public OffsetDateTime getCollectedAt() { return collectedAt; }
    public OffsetDateTime getResultedAt() { return resultedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
