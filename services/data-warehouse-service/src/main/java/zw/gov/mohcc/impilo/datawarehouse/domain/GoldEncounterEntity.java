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
@Table(name = "dwh_gold_encounter")
public class GoldEncounterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "encounter_id", nullable = false, length = 255)
    private String encounterId;

    @Column(name = "patient_id", nullable = false, length = 255)
    private String patientId;

    @Column(name = "facility_id", length = 255)
    private String facilityId;

    @Column(name = "encounter_type", length = 128)
    private String encounterType;

    @Column(name = "encounter_status", length = 64)
    private String encounterStatus;

    @Column(name = "start_date")
    private OffsetDateTime startDate;

    @Column(name = "end_date")
    private OffsetDateTime endDate;

    @Column(name = "provider_id", length = 255)
    private String providerId;

    @Column(name = "diagnosis_codes", columnDefinition = "TEXT")
    private String diagnosisCodes;

    @Column(name = "source_event_id", nullable = false, length = 255)
    private String sourceEventId;

    @Column(name = "source_event_type", nullable = false, length = 128)
    private String sourceEventType;

    @Column(name = "materialized_at", nullable = false)
    private OffsetDateTime materializedAt = OffsetDateTime.now();

    public GoldEncounterEntity() {}

    // Getters
    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getEncounterId() { return encounterId; }
    public String getPatientId() { return patientId; }
    public String getFacilityId() { return facilityId; }
    public String getEncounterType() { return encounterType; }
    public String getEncounterStatus() { return encounterStatus; }
    public OffsetDateTime getStartDate() { return startDate; }
    public OffsetDateTime getEndDate() { return endDate; }
    public String getProviderId() { return providerId; }
    public String getDiagnosisCodes() { return diagnosisCodes; }
    public String getSourceEventId() { return sourceEventId; }
    public String getSourceEventType() { return sourceEventType; }
    public OffsetDateTime getMaterializedAt() { return materializedAt; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public void setEncounterId(String encounterId) { this.encounterId = encounterId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }
    public void setFacilityId(String facilityId) { this.facilityId = facilityId; }
    public void setEncounterType(String encounterType) { this.encounterType = encounterType; }
    public void setEncounterStatus(String encounterStatus) { this.encounterStatus = encounterStatus; }
    public void setStartDate(OffsetDateTime startDate) { this.startDate = startDate; }
    public void setEndDate(OffsetDateTime endDate) { this.endDate = endDate; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public void setDiagnosisCodes(String diagnosisCodes) { this.diagnosisCodes = diagnosisCodes; }
    public void setSourceEventId(String sourceEventId) { this.sourceEventId = sourceEventId; }
    public void setSourceEventType(String sourceEventType) { this.sourceEventType = sourceEventType; }
    public void setMaterializedAt(OffsetDateTime materializedAt) { this.materializedAt = materializedAt; }
}
