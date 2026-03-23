package zw.gov.mohcc.impilo.ndr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ndr_bronze_event")
public class BronzeEventEntity {

    @Id
    @Column(name = "receipt_id", nullable = false)
    private UUID receiptId = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pod_id", nullable = false, length = 64)
    private String podId;

    @Column(name = "request_id", nullable = false, length = 255)
    private String requestId;

    @Column(name = "correlation_id", nullable = false, length = 255)
    private String correlationId;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "event_id", nullable = false, length = 255)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "emitted_at")
    private OffsetDateTime emittedAt;

    @Column(name = "subject_type", nullable = false, length = 64)
    private String subjectType;

    @Column(name = "subject_id", nullable = false, length = 255)
    private String subjectId;

    @Column(name = "partition_key", nullable = false, length = 255)
    private String partitionKey;

    @Column(name = "envelope_json", nullable = false, columnDefinition = "TEXT")
    private String envelopeJson;

    @Column(name = "stored_at", nullable = false)
    private OffsetDateTime storedAt = OffsetDateTime.now();

    public BronzeEventEntity() {}

    public UUID getReceiptId() { return receiptId; }
    public UUID getTenantId() { return tenantId; }
    public String getPodId() { return podId; }
    public String getRequestId() { return requestId; }
    public String getCorrelationId() { return correlationId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public int getSchemaVersion() { return schemaVersion; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public OffsetDateTime getEmittedAt() { return emittedAt; }
    public String getSubjectType() { return subjectType; }
    public String getSubjectId() { return subjectId; }
    public String getPartitionKey() { return partitionKey; }
    public String getEnvelopeJson() { return envelopeJson; }
    public OffsetDateTime getStoredAt() { return storedAt; }

    public void setReceiptId(UUID receiptId) { this.receiptId = receiptId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public void setPodId(String podId) { this.podId = podId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }
    public void setEmittedAt(OffsetDateTime emittedAt) { this.emittedAt = emittedAt; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
    public void setPartitionKey(String partitionKey) { this.partitionKey = partitionKey; }
    public void setEnvelopeJson(String envelopeJson) { this.envelopeJson = envelopeJson; }
    public void setStoredAt(OffsetDateTime storedAt) { this.storedAt = storedAt; }
}
