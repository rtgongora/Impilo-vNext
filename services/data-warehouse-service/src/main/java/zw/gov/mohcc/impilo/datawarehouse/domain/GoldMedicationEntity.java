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
@Table(name = "dwh_gold_medication")
public class GoldMedicationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "medication_id", nullable = false, length = 255)
    private String medicationId;

    @Column(name = "patient_id", nullable = false, length = 255)
    private String patientId;

    @Column(name = "encounter_id", length = 255)
    private String encounterId;

    @Column(name = "facility_id", length = 255)
    private String facilityId;

    @Column(name = "drug_code", length = 128)
    private String drugCode;

    @Column(name = "drug_name", length = 512)
    private String drugName;

    @Column(name = "dosage", length = 255)
    private String dosage;

    @Column(name = "frequency", length = 128)
    private String frequency;

    @Column(name = "route", length = 128)
    private String route;

    @Column(name = "prescribed_date")
    private OffsetDateTime prescribedDate;

    @Column(name = "dispensed_date")
    private OffsetDateTime dispensedDate;

    @Column(name = "prescriber_id", length = 255)
    private String prescriberId;

    @Column(name = "source_event_id", nullable = false, length = 255)
    private String sourceEventId;

    @Column(name = "source_event_type", nullable = false, length = 128)
    private String sourceEventType;

    @Column(name = "materialized_at", nullable = false)
    private OffsetDateTime materializedAt = OffsetDateTime.now();

    public GoldMedicationEntity() {}

    // Getters
    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getMedicationId() { return medicationId; }
    public String getPatientId() { return patientId; }
    public String getEncounterId() { return encounterId; }
    public String getFacilityId() { return facilityId; }
    public String getDrugCode() { return drugCode; }
    public String getDrugName() { return drugName; }
    public String getDosage() { return dosage; }
    public String getFrequency() { return frequency; }
    public String getRoute() { return route; }
    public OffsetDateTime getPrescribedDate() { return prescribedDate; }
    public OffsetDateTime getDispensedDate() { return dispensedDate; }
    public String getPrescriberId() { return prescriberId; }
    public String getSourceEventId() { return sourceEventId; }
    public String getSourceEventType() { return sourceEventType; }
    public OffsetDateTime getMaterializedAt() { return materializedAt; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public void setMedicationId(String medicationId) { this.medicationId = medicationId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }
    public void setEncounterId(String encounterId) { this.encounterId = encounterId; }
    public void setFacilityId(String facilityId) { this.facilityId = facilityId; }
    public void setDrugCode(String drugCode) { this.drugCode = drugCode; }
    public void setDrugName(String drugName) { this.drugName = drugName; }
    public void setDosage(String dosage) { this.dosage = dosage; }
    public void setFrequency(String frequency) { this.frequency = frequency; }
    public void setRoute(String route) { this.route = route; }
    public void setPrescribedDate(OffsetDateTime prescribedDate) { this.prescribedDate = prescribedDate; }
    public void setDispensedDate(OffsetDateTime dispensedDate) { this.dispensedDate = dispensedDate; }
    public void setPrescriberId(String prescriberId) { this.prescriberId = prescriberId; }
    public void setSourceEventId(String sourceEventId) { this.sourceEventId = sourceEventId; }
    public void setSourceEventType(String sourceEventType) { this.sourceEventType = sourceEventType; }
    public void setMaterializedAt(OffsetDateTime materializedAt) { this.materializedAt = materializedAt; }
}
