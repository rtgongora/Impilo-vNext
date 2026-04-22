package zw.gov.mohcc.impilo.msikaflow.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "mf_handoff_events")
public class HandoffEventEntity {

    @Id
    @Column(name = "handoff_id", nullable = false, length = 26)
    private String handoffId;

    @Column(name = "fulfillment_id", length = 26)
    private String fulfillmentId;

    @Column(name = "leg_id", length = 26)
    private String legId;

    @Column(name = "handoff_type", nullable = false, length = 40)
    private String handoffType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "from_party", nullable = false, columnDefinition = "jsonb")
    private String fromPartyJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "to_party", nullable = false, columnDefinition = "jsonb")
    private String toPartyJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "location", nullable = false, columnDefinition = "jsonb")
    private String locationJson = "{}";

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "integrity_outcome", length = 40)
    private String integrityOutcome;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadataJson = "{}";

    @PrePersist
    protected void onCreate() {
        if (occurredAt == null) occurredAt = OffsetDateTime.now();
        if (fromPartyJson == null) fromPartyJson = "{}";
        if (toPartyJson == null) toPartyJson = "{}";
        if (locationJson == null) locationJson = "{}";
        if (metadataJson == null) metadataJson = "{}";
    }

    public String getHandoffId() { return handoffId; }
    public void setHandoffId(String handoffId) { this.handoffId = handoffId; }
    public String getFulfillmentId() { return fulfillmentId; }
    public void setFulfillmentId(String fulfillmentId) { this.fulfillmentId = fulfillmentId; }
    public String getLegId() { return legId; }
    public void setLegId(String legId) { this.legId = legId; }
    public String getHandoffType() { return handoffType; }
    public void setHandoffType(String handoffType) { this.handoffType = handoffType; }
    public String getFromPartyJson() { return fromPartyJson; }
    public void setFromPartyJson(String fromPartyJson) { this.fromPartyJson = fromPartyJson; }
    public String getToPartyJson() { return toPartyJson; }
    public void setToPartyJson(String toPartyJson) { this.toPartyJson = toPartyJson; }
    public String getLocationJson() { return locationJson; }
    public void setLocationJson(String locationJson) { this.locationJson = locationJson; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }
    public String getIntegrityOutcome() { return integrityOutcome; }
    public void setIntegrityOutcome(String integrityOutcome) { this.integrityOutcome = integrityOutcome; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}

