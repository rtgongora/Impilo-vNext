package zw.gov.mohcc.impilo.inventory.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A single controlled-drug movement in the witness register: issue, administer,
 * waste or discard, with the administering and witnessing providers and a
 * carried-forward running balance per (tenant, item, reference).
 */
@Entity
@Table(name = "inv_controlled_drug_register")
public class ControlledDrugRegisterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "entry_id", nullable = false)
    private UUID entryId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "store_id")
    private UUID storeId;

    @Column(name = "item_code", nullable = false, length = 64)
    private String itemCode;

    @Column(name = "drug_name", length = 255)
    private String drugName;

    @Column(name = "action", nullable = false, length = 16)
    private String action;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit", length = 24)
    private String unit;

    @Column(name = "running_balance", precision = 12, scale = 3)
    private BigDecimal runningBalance;

    @Column(name = "administering_provider", length = 128)
    private String administeringProvider;

    @Column(name = "witness_provider", length = 128)
    private String witnessProvider;

    @Column(name = "ref_type", length = 40)
    private String refType = "THEATRE_CASE";

    @Column(name = "ref_id", length = 128)
    private String refId;

    @Column(name = "chart_entry_ref", length = 128)
    private String chartEntryRef;

    @Column(name = "reconciled", nullable = false)
    private boolean reconciled = false;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public UUID getEntryId() { return entryId; }
    public void setEntryId(UUID entryId) { this.entryId = entryId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public UUID getStoreId() { return storeId; }
    public void setStoreId(UUID storeId) { this.storeId = storeId; }

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }

    public String getDrugName() { return drugName; }
    public void setDrugName(String drugName) { this.drugName = drugName; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public BigDecimal getRunningBalance() { return runningBalance; }
    public void setRunningBalance(BigDecimal runningBalance) { this.runningBalance = runningBalance; }

    public String getAdministeringProvider() { return administeringProvider; }
    public void setAdministeringProvider(String administeringProvider) { this.administeringProvider = administeringProvider; }

    public String getWitnessProvider() { return witnessProvider; }
    public void setWitnessProvider(String witnessProvider) { this.witnessProvider = witnessProvider; }

    public String getRefType() { return refType; }
    public void setRefType(String refType) { this.refType = refType; }

    public String getRefId() { return refId; }
    public void setRefId(String refId) { this.refId = refId; }

    public String getChartEntryRef() { return chartEntryRef; }
    public void setChartEntryRef(String chartEntryRef) { this.chartEntryRef = chartEntryRef; }

    public boolean isReconciled() { return reconciled; }
    public void setReconciled(boolean reconciled) { this.reconciled = reconciled; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
