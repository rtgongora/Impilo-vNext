package zw.gov.mohcc.impilo.nhume.domain;

import jakarta.persistence.Column;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "nhume_chain_of_custody_events")
public class ChainOfCustodyEventEntity {

    @Id
    @Column(name = "coc_id", nullable = false)
    private UUID cocId;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "package_id")
    private UUID packageId;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "event_kind", nullable = false, length = 48)
    private String eventKind;

    @Column(name = "custody_holder", length = 255)
    private String custodyHolder;

    @Column(name = "custody_location", length = 255)
    private String custodyLocation;

    @Column(name = "seal_id", length = 128)
    private String sealId;

    @Column(name = "temperature_c")
    private BigDecimal temperatureC;

    @Column(name = "handover_notes", columnDefinition = "TEXT")
    private String handoverNotes;

    @Column(name = "verification", length = 48)
    private String verification;

    @Column(name = "actor_id", length = 255)
    private String actorId;

    @Column(name = "actor_type", length = 48)
    private String actorType;

    @Column(name = "device_used", length = 255)
    private String deviceUsed;

    @Column(name = "evidence_uri", length = 1024)
    private String evidenceUri;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "exception_flags", nullable = false, columnDefinition = "jsonb")
    private String exceptionFlags = "[]";

    @Column(name = "audit_event_id")
    private UUID auditEventId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public ChainOfCustodyEventEntity() {}

    public UUID getCocId() { return cocId; }
    public void setCocId(UUID v) { this.cocId = v; }
    public UUID getDeliveryId() { return deliveryId; }
    public void setDeliveryId(UUID v) { this.deliveryId = v; }
    public UUID getPackageId() { return packageId; }
    public void setPackageId(UUID v) { this.packageId = v; }
    public int getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(int v) { this.sequenceNo = v; }
    public String getEventKind() { return eventKind; }
    public void setEventKind(String v) { this.eventKind = v; }
    public String getCustodyHolder() { return custodyHolder; }
    public void setCustodyHolder(String v) { this.custodyHolder = v; }
    public String getCustodyLocation() { return custodyLocation; }
    public void setCustodyLocation(String v) { this.custodyLocation = v; }
    public String getSealId() { return sealId; }
    public void setSealId(String v) { this.sealId = v; }
    public BigDecimal getTemperatureC() { return temperatureC; }
    public void setTemperatureC(BigDecimal v) { this.temperatureC = v; }
    public String getHandoverNotes() { return handoverNotes; }
    public void setHandoverNotes(String v) { this.handoverNotes = v; }
    public String getVerification() { return verification; }
    public void setVerification(String v) { this.verification = v; }
    public String getActorId() { return actorId; }
    public void setActorId(String v) { this.actorId = v; }
    public String getActorType() { return actorType; }
    public void setActorType(String v) { this.actorType = v; }
    public String getDeviceUsed() { return deviceUsed; }
    public void setDeviceUsed(String v) { this.deviceUsed = v; }
    public String getEvidenceUri() { return evidenceUri; }
    public void setEvidenceUri(String v) { this.evidenceUri = v; }
    public String getExceptionFlags() { return exceptionFlags; }
    public void setExceptionFlags(String v) { this.exceptionFlags = v; }
    public UUID getAuditEventId() { return auditEventId; }
    public void setAuditEventId(UUID v) { this.auditEventId = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
