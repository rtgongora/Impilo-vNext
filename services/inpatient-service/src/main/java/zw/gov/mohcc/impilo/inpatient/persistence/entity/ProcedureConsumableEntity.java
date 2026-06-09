package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "procedure_consumable", schema = "inpatient")
public class ProcedureConsumableEntity {

    @Id
    @Column(name = "consumable_id")
    private UUID consumableId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "item_code", nullable = false)
    private String itemCode;

    @Column(name = "item_name")
    private String itemName;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "inventory_ledger_ref")
    private String inventoryLedgerRef;

    @Column(name = "recorded_by")
    private String recordedBy;

    @Column(name = "recorded_at")
    private OffsetDateTime recordedAt;

    @PrePersist
    void onCreate() {
        if (consumableId == null) consumableId = UUID.randomUUID();
        if (recordedAt == null) recordedAt = OffsetDateTime.now();
    }

    public UUID getConsumableId() { return consumableId; }
    public void setConsumableId(UUID consumableId) { this.consumableId = consumableId; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID episodeId) { this.episodeId = episodeId; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public UUID getStoreId() { return storeId; }
    public void setStoreId(UUID storeId) { this.storeId = storeId; }
    public String getInventoryLedgerRef() { return inventoryLedgerRef; }
    public void setInventoryLedgerRef(String inventoryLedgerRef) { this.inventoryLedgerRef = inventoryLedgerRef; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime recordedAt) { this.recordedAt = recordedAt; }
}
