package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A single time-series PACU (recovery) observation for a procedure episode (Wave 4 §12).
 * The PACU stay is its own tracked phase after surgery COMPLETED — this is a projection/link row
 * keyed by episode_id; the episode remains the case SoR.
 */
@Entity
@Table(name = "procedure_pacu_observation", schema = "inpatient")
public class ProcedurePacuObservationEntity {

    @Id
    @Column(name = "observation_id")
    private UUID observationId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "seq", nullable = false)
    private int seq;

    @Column(name = "observed_at", nullable = false)
    private OffsetDateTime observedAt;

    @Column(name = "airway")
    private String airway;

    @Column(name = "breathing")
    private String breathing;

    @Column(name = "spo2")
    private Integer spo2;

    @Column(name = "systolic_bp")
    private Integer systolicBp;

    @Column(name = "diastolic_bp")
    private Integer diastolicBp;

    @Column(name = "heart_rate")
    private Integer heartRate;

    @Column(name = "consciousness")
    private String consciousness;

    @Column(name = "pain_score")
    private Integer painScore;

    @Column(name = "nausea_vomiting")
    private String nauseaVomiting;

    @Column(name = "bleeding_wound")
    private String bleedingWound;

    @Column(name = "temperature_c")
    private BigDecimal temperatureC;

    @Column(name = "urine_output_ml")
    private Integer urineOutputMl;

    @Column(name = "fluids")
    private String fluids;

    @Column(name = "drains_devices")
    private String drainsDevices;

    @Column(name = "medications")
    private String medications;

    @Column(name = "aldrete_score")
    private Integer aldreteScore;

    @Column(name = "discharge_readiness_band")
    private String dischargeReadinessBand;

    @Column(name = "notes")
    private String notes;

    @Column(name = "recorded_by")
    private String recordedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (observationId == null) observationId = UUID.randomUUID();
        if (observedAt == null) observedAt = OffsetDateTime.now();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getObservationId() { return observationId; }
    public void setObservationId(UUID v) { this.observationId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID v) { this.episodeId = v; }
    public int getSeq() { return seq; }
    public void setSeq(int v) { this.seq = v; }
    public OffsetDateTime getObservedAt() { return observedAt; }
    public void setObservedAt(OffsetDateTime v) { this.observedAt = v; }
    public String getAirway() { return airway; }
    public void setAirway(String v) { this.airway = v; }
    public String getBreathing() { return breathing; }
    public void setBreathing(String v) { this.breathing = v; }
    public Integer getSpo2() { return spo2; }
    public void setSpo2(Integer v) { this.spo2 = v; }
    public Integer getSystolicBp() { return systolicBp; }
    public void setSystolicBp(Integer v) { this.systolicBp = v; }
    public Integer getDiastolicBp() { return diastolicBp; }
    public void setDiastolicBp(Integer v) { this.diastolicBp = v; }
    public Integer getHeartRate() { return heartRate; }
    public void setHeartRate(Integer v) { this.heartRate = v; }
    public String getConsciousness() { return consciousness; }
    public void setConsciousness(String v) { this.consciousness = v; }
    public Integer getPainScore() { return painScore; }
    public void setPainScore(Integer v) { this.painScore = v; }
    public String getNauseaVomiting() { return nauseaVomiting; }
    public void setNauseaVomiting(String v) { this.nauseaVomiting = v; }
    public String getBleedingWound() { return bleedingWound; }
    public void setBleedingWound(String v) { this.bleedingWound = v; }
    public BigDecimal getTemperatureC() { return temperatureC; }
    public void setTemperatureC(BigDecimal v) { this.temperatureC = v; }
    public Integer getUrineOutputMl() { return urineOutputMl; }
    public void setUrineOutputMl(Integer v) { this.urineOutputMl = v; }
    public String getFluids() { return fluids; }
    public void setFluids(String v) { this.fluids = v; }
    public String getDrainsDevices() { return drainsDevices; }
    public void setDrainsDevices(String v) { this.drainsDevices = v; }
    public String getMedications() { return medications; }
    public void setMedications(String v) { this.medications = v; }
    public Integer getAldreteScore() { return aldreteScore; }
    public void setAldreteScore(Integer v) { this.aldreteScore = v; }
    public String getDischargeReadinessBand() { return dischargeReadinessBand; }
    public void setDischargeReadinessBand(String v) { this.dischargeReadinessBand = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String v) { this.recordedBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
