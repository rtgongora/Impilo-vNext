package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A single timed ABCDE resuscitation observation on the canonical trauma episode. Multi-channel
 * (AIRWAY/BREATHING/CIRCULATION/DISABILITY/EXPOSURE/DRUG/RHYTHM/CPR) repeated-entry time-series that
 * replaces the old hardcoded-vitals / checklist-JSON demo (architecture decision #3, W5).
 */
@Entity
@Table(name = "resuscitation_event", schema = "inpatient")
public class ResuscitationEventEntity {

    @Id @Column(name = "id") private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "activation_id", nullable = false) private UUID activationId;
    @Column(name = "trauma_episode_id") private UUID traumaEpisodeId;
    @Column(name = "resus_id") private UUID resusId;
    @Column(name = "channel", nullable = false, length = 24) private String channel;
    @Column(name = "code", length = 64) private String code;
    @Column(name = "value_text", length = 255) private String valueText;
    @Column(name = "value_numeric") private BigDecimal valueNumeric;
    @Column(name = "unit", length = 32) private String unit;
    @Column(name = "recorded_at", nullable = false) private OffsetDateTime recordedAt;
    @Column(name = "actor_id", length = 128) private String actorId;
    @Column(name = "notes", columnDefinition = "TEXT") private String notes;
    /** Idempotent-retry token (V201, W6b) — a device retrying a blind write must never double-count. */
    @Column(name = "client_event_id", length = 128) private String clientEventId;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;

    @PrePersist void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (recordedAt == null) recordedAt = now;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getActivationId() { return activationId; }
    public void setActivationId(UUID v) { this.activationId = v; }
    public UUID getTraumaEpisodeId() { return traumaEpisodeId; }
    public void setTraumaEpisodeId(UUID v) { this.traumaEpisodeId = v; }
    public UUID getResusId() { return resusId; }
    public void setResusId(UUID v) { this.resusId = v; }
    public String getChannel() { return channel; }
    public void setChannel(String v) { this.channel = v; }
    public String getCode() { return code; }
    public void setCode(String v) { this.code = v; }
    public String getValueText() { return valueText; }
    public void setValueText(String v) { this.valueText = v; }
    public BigDecimal getValueNumeric() { return valueNumeric; }
    public void setValueNumeric(BigDecimal v) { this.valueNumeric = v; }
    public String getUnit() { return unit; }
    public void setUnit(String v) { this.unit = v; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime v) { this.recordedAt = v; }
    public String getActorId() { return actorId; }
    public void setActorId(String v) { this.actorId = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public String getClientEventId() { return clientEventId; }
    public void setClientEventId(String v) { this.clientEventId = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
