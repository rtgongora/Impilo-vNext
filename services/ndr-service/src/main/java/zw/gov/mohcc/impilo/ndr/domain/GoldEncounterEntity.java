package zw.gov.mohcc.impilo.ndr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ndr_gold_encounter")
public class GoldEncounterEntity {

    @Id
    @Column(name = "encounter_id", nullable = false)
    private UUID encounterId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_ref", nullable = false, length = 255)
    private String patientRef;

    @Column(name = "facility_ref", length = 255)
    private String facilityRef;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "status", nullable = false, length = 64)
    private String status = "UNKNOWN";

    @Column(name = "source_event_id", nullable = false, length = 255)
    private String sourceEventId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected GoldEncounterEntity() {}

    public UUID getEncounterId() { return encounterId; }
    public UUID getTenantId() { return tenantId; }
    public String getPatientRef() { return patientRef; }
    public String getFacilityRef() { return facilityRef; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public String getStatus() { return status; }
    public String getSourceEventId() { return sourceEventId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setEncounterId(UUID encounterId) { this.encounterId = encounterId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public void setPatientRef(String patientRef) { this.patientRef = patientRef; }
    public void setFacilityRef(String facilityRef) { this.facilityRef = facilityRef; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public void setStatus(String status) { this.status = status; }
    public void setSourceEventId(String sourceEventId) { this.sourceEventId = sourceEventId; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
