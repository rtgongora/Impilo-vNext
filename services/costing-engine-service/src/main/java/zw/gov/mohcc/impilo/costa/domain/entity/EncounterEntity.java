package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;
import zw.gov.mohcc.impilo.costa.domain.enums.EncounterStatus;
import zw.gov.mohcc.impilo.costa.domain.enums.EncounterType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "costa_encounters")
public class EncounterEntity {

    @Id
    @Column(name = "encounter_id", length = 26)
    private String encounterId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "patient_cpid", nullable = false, length = 50)
    private String patientCpid;

    @Enumerated(EnumType.STRING)
    @Column(name = "encounter_type", nullable = false, length = 30)
    private EncounterType encounterType = EncounterType.OUTPATIENT;

    @Column(name = "pct_journey_id", length = 50)
    private String pctJourneyId;

    @Column(name = "pct_ref", nullable = false, columnDefinition = "jsonb")
    private String pctRef = "{}";

    @Column(name = "insurance_plan_id")
    private Long insurancePlanId;

    @Column(name = "exemption_category", length = 50)
    private String exemptionCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EncounterStatus status = EncounterStatus.OPEN;

    @Column(name = "opened_at", nullable = false)
    private OffsetDateTime openedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (openedAt == null) openedAt = OffsetDateTime.now();
    }

    public String getEncounterId() { return encounterId; }
    public void setEncounterId(String encounterId) { this.encounterId = encounterId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public String getPatientCpid() { return patientCpid; }
    public void setPatientCpid(String patientCpid) { this.patientCpid = patientCpid; }
    public EncounterType getEncounterType() { return encounterType; }
    public void setEncounterType(EncounterType encounterType) { this.encounterType = encounterType; }
    public String getPctJourneyId() { return pctJourneyId; }
    public void setPctJourneyId(String pctJourneyId) { this.pctJourneyId = pctJourneyId; }
    public String getPctRef() { return pctRef; }
    public void setPctRef(String pctRef) { this.pctRef = pctRef; }
    public Long getInsurancePlanId() { return insurancePlanId; }
    public void setInsurancePlanId(Long insurancePlanId) { this.insurancePlanId = insurancePlanId; }
    public String getExemptionCategory() { return exemptionCategory; }
    public void setExemptionCategory(String exemptionCategory) { this.exemptionCategory = exemptionCategory; }
    public EncounterStatus getStatus() { return status; }
    public void setStatus(EncounterStatus status) { this.status = status; }
    public OffsetDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(OffsetDateTime openedAt) { this.openedAt = openedAt; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(OffsetDateTime closedAt) { this.closedAt = closedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
