package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One entry on the intra-op anaesthesia chart, across channels (AGENT/FLUID/BLOOD/MEDICATION/
 * MONITORING/VITAL/EVENT). BLOOD-channel entries carry madi_transfusion_episode_id (projected from
 * procedure_blood_link — no re-entry). The chart read returns the ordered multi-channel time series.
 */
@Entity
@Table(name = "procedure_anaesthesia_chart_entry", schema = "inpatient")
public class ProcedureAnaesthesiaChartEntryEntity {

    @Id
    @Column(name = "entry_id")
    private UUID entryId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "channel", nullable = false)
    private String channel;

    @Column(name = "seq", nullable = false)
    private int seq = 1;

    @Column(name = "technique")
    private String technique;

    @Column(name = "label")
    private String label;

    @Column(name = "value_numeric")
    private BigDecimal valueNumeric;

    @Column(name = "value_text")
    private String valueText;

    @Column(name = "unit")
    private String unit;

    @Column(name = "detail_json", columnDefinition = "TEXT")
    private String detailJson;

    @Column(name = "madi_transfusion_episode_id")
    private String madiTransfusionEpisodeId;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    @Column(name = "recorded_by")
    private String recordedBy;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (entryId == null) entryId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (recordedAt == null) recordedAt = now;
        if (createdAt == null) createdAt = now;
    }

    public UUID getEntryId() { return entryId; }
    public void setEntryId(UUID v) { this.entryId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID v) { this.episodeId = v; }
    public String getChannel() { return channel; }
    public void setChannel(String v) { this.channel = v; }
    public int getSeq() { return seq; }
    public void setSeq(int v) { this.seq = v; }
    public String getTechnique() { return technique; }
    public void setTechnique(String v) { this.technique = v; }
    public String getLabel() { return label; }
    public void setLabel(String v) { this.label = v; }
    public BigDecimal getValueNumeric() { return valueNumeric; }
    public void setValueNumeric(BigDecimal v) { this.valueNumeric = v; }
    public String getValueText() { return valueText; }
    public void setValueText(String v) { this.valueText = v; }
    public String getUnit() { return unit; }
    public void setUnit(String v) { this.unit = v; }
    public String getDetailJson() { return detailJson; }
    public void setDetailJson(String v) { this.detailJson = v; }
    public String getMadiTransfusionEpisodeId() { return madiTransfusionEpisodeId; }
    public void setMadiTransfusionEpisodeId(String v) { this.madiTransfusionEpisodeId = v; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime v) { this.recordedAt = v; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String v) { this.recordedBy = v; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String v) { this.idempotencyKey = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
