package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A structured medication administration during a resuscitation (inpatient V200, W6a). Replaces
 * resuscitation_record.medications_json for all new entries — see the migration header for why the
 * free-text column is left in place rather than dropped.
 */
@Entity
@Table(name = "resuscitation_medication", schema = "inpatient")
public class ResuscitationMedicationEntity {

    @Id
    @Column(name = "med_id")
    private UUID medId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "activation_id", nullable = false)
    private UUID activationId;

    @Column(name = "trauma_episode_id")
    private UUID traumaEpisodeId;

    @Column(name = "resus_id")
    private UUID resusId;

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

    @Column(name = "administered_at", nullable = false)
    private OffsetDateTime administeredAt;

    @Column(name = "administered_by", nullable = false)
    private String administeredBy;

    /** Optional second-person confirmation — not mandatory here; see the migration's column comment. */
    @Column(name = "witnessed_by")
    private String witnessedBy;

    /** Idempotent-retry key. Any device mints its own; a blind retry must not double-dose. */
    @Column(name = "client_event_id")
    private String clientEventId;

    /** Correct, never delete: a new row supersedes an old one rather than rewriting history. */
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
        if (medId == null) medId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (administeredAt == null) administeredAt = now;
        if (createdAt == null) createdAt = now;
    }

    public UUID getMedId() { return medId; }
    public void setMedId(UUID medId) { this.medId = medId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getActivationId() { return activationId; }
    public void setActivationId(UUID activationId) { this.activationId = activationId; }
    public UUID getTraumaEpisodeId() { return traumaEpisodeId; }
    public void setTraumaEpisodeId(UUID traumaEpisodeId) { this.traumaEpisodeId = traumaEpisodeId; }
    public UUID getResusId() { return resusId; }
    public void setResusId(UUID resusId) { this.resusId = resusId; }
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
    public OffsetDateTime getAdministeredAt() { return administeredAt; }
    public void setAdministeredAt(OffsetDateTime administeredAt) { this.administeredAt = administeredAt; }
    public String getAdministeredBy() { return administeredBy; }
    public void setAdministeredBy(String administeredBy) { this.administeredBy = administeredBy; }
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
