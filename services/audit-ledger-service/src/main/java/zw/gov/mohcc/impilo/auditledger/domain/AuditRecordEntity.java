package zw.gov.mohcc.impilo.auditledger.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ald_audit_records")
public class AuditRecordEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "record_id", nullable = false, updatable = false) private UUID recordId;
    @Column(name = "tenant_id", nullable = false, updatable = false) private UUID tenantId;
    @Column(name = "sequence_num", nullable = false, updatable = false) private long sequenceNum;
    @Column(name = "correlation_id", nullable = false, updatable = false) private UUID correlationId;
    @Column(name = "actor_id", nullable = false, updatable = false) private String actorId;
    @Column(name = "actor_type", nullable = false, updatable = false) private String actorType;
    @Column(nullable = false, updatable = false) private String action;
    @Column(name = "resource_type", nullable = false, updatable = false) private String resourceType;
    @Column(name = "resource_id", updatable = false) private String resourceId;
    @Column(nullable = false, updatable = false) private String outcome = "SUCCESS";
    @Column(name = "detail_json", columnDefinition = "TEXT", updatable = false) private String detailJson;
    @Column(name = "occurred_at", nullable = false, updatable = false) private OffsetDateTime occurredAt;
    @Column(name = "prev_hash", updatable = false) private String prevHash;
    @Column(name = "entry_hash", nullable = false, updatable = false) private String entryHash;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt = OffsetDateTime.now();

    protected AuditRecordEntity() {}

    public AuditRecordEntity(UUID recordId, UUID tenantId, long sequenceNum, UUID correlationId,
                              String actorId, String actorType, String action, String resourceType,
                              String resourceId, String outcome, String detailJson,
                              OffsetDateTime occurredAt, String prevHash, String entryHash) {
        this.recordId = recordId; this.tenantId = tenantId; this.sequenceNum = sequenceNum;
        this.correlationId = correlationId; this.actorId = actorId; this.actorType = actorType;
        this.action = action; this.resourceType = resourceType; this.resourceId = resourceId;
        this.outcome = outcome; this.detailJson = detailJson; this.occurredAt = occurredAt;
        this.prevHash = prevHash; this.entryHash = entryHash;
    }

    public Long getId() { return id; }
    public UUID getRecordId() { return recordId; }
    public UUID getTenantId() { return tenantId; }
    public long getSequenceNum() { return sequenceNum; }
    public UUID getCorrelationId() { return correlationId; }
    public String getActorId() { return actorId; }
    public String getActorType() { return actorType; }
    public String getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public String getOutcome() { return outcome; }
    public String getDetailJson() { return detailJson; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public String getPrevHash() { return prevHash; }
    public String getEntryHash() { return entryHash; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
