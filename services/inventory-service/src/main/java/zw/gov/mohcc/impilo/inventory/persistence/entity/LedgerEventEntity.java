package zw.gov.mohcc.impilo.inventory.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import zw.gov.mohcc.impilo.inventory.domain.LedgerEventType;

/**
 * Immutable ledger entry recording a stock quantity change.
 * Stock balance is derived by summing qtyDelta for a given facility/store/item combination.
 *
 * <p>This entity is immutable after creation: event_id and created_at have no setters.
 * Each event carries an idempotency key to prevent duplicate processing.</p>
 */
@Entity
@Table(name = "inv_ledger_events")
public class LedgerEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "bin_id")
    private UUID binId;

    @Column(name = "item_code", nullable = false)
    private String itemCode;

    @Column(name = "batch")
    private String batch;

    @Column(name = "expiry")
    private LocalDate expiry;

    @Column(name = "qty_delta", nullable = false)
    private int qtyDelta;

    @Column(name = "uom")
    private String uom;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private LedgerEventType eventType;

    @Column(name = "reason")
    private String reason;

    @Column(name = "ref_type")
    private String refType;

    @Column(name = "ref_id")
    private String refId;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "actor_id")
    private String actorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // Getters (all fields) and setters (mutable fields only — eventId and createdAt are immutable)

    public UUID getEventId() { return eventId; }
    // No setter for eventId — immutable after creation

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public UUID getStoreId() { return storeId; }
    public void setStoreId(UUID storeId) { this.storeId = storeId; }

    public UUID getBinId() { return binId; }
    public void setBinId(UUID binId) { this.binId = binId; }

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }

    public String getBatch() { return batch; }
    public void setBatch(String batch) { this.batch = batch; }

    public LocalDate getExpiry() { return expiry; }
    public void setExpiry(LocalDate expiry) { this.expiry = expiry; }

    public int getQtyDelta() { return qtyDelta; }
    public void setQtyDelta(int qtyDelta) { this.qtyDelta = qtyDelta; }

    public String getUom() { return uom; }
    public void setUom(String uom) { this.uom = uom; }

    public LedgerEventType getEventType() { return eventType; }
    public void setEventType(LedgerEventType eventType) { this.eventType = eventType; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getRefType() { return refType; }
    public void setRefType(String refType) { this.refType = refType; }

    public String getRefId() { return refId; }
    public void setRefId(String refId) { this.refId = refId; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    // No setter for createdAt — immutable after creation
}
