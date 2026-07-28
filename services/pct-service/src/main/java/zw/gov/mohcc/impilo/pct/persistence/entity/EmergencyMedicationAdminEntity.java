package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * ED / non-admitted medication administration (pct V207, W8b). Distinct from inpatient's
 * resuscitation_medication (the in-resus, CPR-time-critical local write) — this is for a patient who
 * is NOT in an active resuscitation, where the full authoriser/witness trail is affordable.
 */
@Entity
@Table(name = "emergency_medication_admin")
public class EmergencyMedicationAdminEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "admin_id")
    private UUID adminId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "visit_id")
    private UUID visitId;

    @Column(name = "trauma_episode_id")
    private UUID traumaEpisodeId;

    @Column(name = "drug_name", nullable = false)
    private String drugName;

    @Column(name = "drug_code")
    private String drugCode;

    @Column(name = "dose_value", nullable = false)
    private BigDecimal doseValue;

    @Column(name = "dose_unit", nullable = false)
    private String doseUnit;

    @Column(name = "route", nullable = false)
    private String route;

    @Column(name = "high_risk", nullable = false)
    private boolean highRisk;

    @Column(name = "administered_at", nullable = false)
    private OffsetDateTime administeredAt;

    @Column(name = "administered_by", nullable = false)
    private String administeredBy;

    @Column(name = "authorised_by")
    private String authorisedBy;

    @Column(name = "witnessed_by")
    private String witnessedBy;

    @Column(name = "client_event_id")
    private String clientEventId;

    @Column(name = "superseded_by")
    private UUID supersededBy;

    @Column(name = "correction_reason", columnDefinition = "TEXT")
    private String correctionReason;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (administeredAt == null) administeredAt = now;
        if (createdAt == null) createdAt = now;
    }

    public UUID getAdminId() { return adminId; }
    public void setAdminId(UUID adminId) { this.adminId = adminId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID episodeId) { this.episodeId = episodeId; }
    public UUID getVisitId() { return visitId; }
    public void setVisitId(UUID visitId) { this.visitId = visitId; }
    public UUID getTraumaEpisodeId() { return traumaEpisodeId; }
    public void setTraumaEpisodeId(UUID traumaEpisodeId) { this.traumaEpisodeId = traumaEpisodeId; }
    public String getDrugName() { return drugName; }
    public void setDrugName(String drugName) { this.drugName = drugName; }
    public String getDrugCode() { return drugCode; }
    public void setDrugCode(String drugCode) { this.drugCode = drugCode; }
    public BigDecimal getDoseValue() { return doseValue; }
    public void setDoseValue(BigDecimal doseValue) { this.doseValue = doseValue; }
    public String getDoseUnit() { return doseUnit; }
    public void setDoseUnit(String doseUnit) { this.doseUnit = doseUnit; }
    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }
    public boolean isHighRisk() { return highRisk; }
    public void setHighRisk(boolean highRisk) { this.highRisk = highRisk; }
    public OffsetDateTime getAdministeredAt() { return administeredAt; }
    public void setAdministeredAt(OffsetDateTime administeredAt) { this.administeredAt = administeredAt; }
    public String getAdministeredBy() { return administeredBy; }
    public void setAdministeredBy(String administeredBy) { this.administeredBy = administeredBy; }
    public String getAuthorisedBy() { return authorisedBy; }
    public void setAuthorisedBy(String authorisedBy) { this.authorisedBy = authorisedBy; }
    public String getWitnessedBy() { return witnessedBy; }
    public void setWitnessedBy(String witnessedBy) { this.witnessedBy = witnessedBy; }
    public String getClientEventId() { return clientEventId; }
    public void setClientEventId(String clientEventId) { this.clientEventId = clientEventId; }
    public UUID getSupersededBy() { return supersededBy; }
    public void setSupersededBy(UUID supersededBy) { this.supersededBy = supersededBy; }
    public String getCorrectionReason() { return correctionReason; }
    public void setCorrectionReason(String correctionReason) { this.correctionReason = correctionReason; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
