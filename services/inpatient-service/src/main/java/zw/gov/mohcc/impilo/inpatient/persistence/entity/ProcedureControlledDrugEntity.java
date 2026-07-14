package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * LINK/PROJECTION row for a controlled-drug register action. inventory-service (DURA) owns the
 * controlled-drug register (two-person witness + running balance); this row holds only the peer
 * register-entry id + a projection, keyed to the theatre case and (optionally) an anaesthesia chart entry.
 */
@Entity
@Table(name = "procedure_controlled_drug", schema = "inpatient")
public class ProcedureControlledDrugEntity {

    @Id
    @Column(name = "record_id")
    private UUID recordId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "seq", nullable = false)
    private int seq = 1;

    @Column(name = "inventory_register_entry_id")
    private String inventoryRegisterEntryId;

    @Column(name = "chart_entry_id")
    private UUID chartEntryId;

    @Column(name = "item_code")
    private String itemCode;

    @Column(name = "drug_name")
    private String drugName;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "quantity")
    private BigDecimal quantity;

    @Column(name = "unit")
    private String unit;

    @Column(name = "running_balance")
    private BigDecimal runningBalance;

    @Column(name = "administering_provider")
    private String administeringProvider;

    @Column(name = "witness_provider")
    private String witnessProvider;

    @Column(name = "status", nullable = false)
    private String status = "RECORDED";

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (recordId == null) recordId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getRecordId() { return recordId; }
    public void setRecordId(UUID v) { this.recordId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID v) { this.episodeId = v; }
    public int getSeq() { return seq; }
    public void setSeq(int v) { this.seq = v; }
    public String getInventoryRegisterEntryId() { return inventoryRegisterEntryId; }
    public void setInventoryRegisterEntryId(String v) { this.inventoryRegisterEntryId = v; }
    public UUID getChartEntryId() { return chartEntryId; }
    public void setChartEntryId(UUID v) { this.chartEntryId = v; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String v) { this.itemCode = v; }
    public String getDrugName() { return drugName; }
    public void setDrugName(String v) { this.drugName = v; }
    public String getAction() { return action; }
    public void setAction(String v) { this.action = v; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal v) { this.quantity = v; }
    public String getUnit() { return unit; }
    public void setUnit(String v) { this.unit = v; }
    public BigDecimal getRunningBalance() { return runningBalance; }
    public void setRunningBalance(BigDecimal v) { this.runningBalance = v; }
    public String getAdministeringProvider() { return administeringProvider; }
    public void setAdministeringProvider(String v) { this.administeringProvider = v; }
    public String getWitnessProvider() { return witnessProvider; }
    public void setWitnessProvider(String v) { this.witnessProvider = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String v) { this.idempotencyKey = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { this.createdBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
