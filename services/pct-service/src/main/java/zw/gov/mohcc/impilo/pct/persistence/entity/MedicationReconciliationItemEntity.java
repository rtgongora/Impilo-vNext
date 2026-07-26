package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One medicine considered — including matches, so checked-and-agreed differs from never-looked-at. See V107__medication_reconciliation.sql. */
@Entity
@Table(name = "pct_medication_reconciliation_items")
public class MedicationReconciliationItemEntity {

    @Id
    @Column(name = "item_id")
    private UUID itemId;

    @Column(name = "reconciliation_id")
    private UUID reconciliationId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "display")
    private String display;

    @Column(name = "code")
    private String code;

    @Column(name = "code_system")
    private String codeSystem;

    @Column(name = "prescription_ref")
    private String prescriptionRef;

    @Column(name = "system_says")
    private String systemSays;

    @Column(name = "patient_says")
    private String patientSays;

    @Column(name = "finding")
    private String finding;

    @Column(name = "action")
    private String action;

    @Column(name = "source")
    private String source;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (itemId == null) itemId = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getItemId() { return itemId; }
    public void setItemId(UUID v) { this.itemId = v; }
    public UUID getReconciliationId() { return reconciliationId; }
    public void setReconciliationId(UUID v) { this.reconciliationId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getDisplay() { return display; }
    public void setDisplay(String v) { this.display = v; }
    public String getCode() { return code; }
    public void setCode(String v) { this.code = v; }
    public String getCodeSystem() { return codeSystem; }
    public void setCodeSystem(String v) { this.codeSystem = v; }
    public String getPrescriptionRef() { return prescriptionRef; }
    public void setPrescriptionRef(String v) { this.prescriptionRef = v; }
    public String getSystemSays() { return systemSays; }
    public void setSystemSays(String v) { this.systemSays = v; }
    public String getPatientSays() { return patientSays; }
    public void setPatientSays(String v) { this.patientSays = v; }
    public String getFinding() { return finding; }
    public void setFinding(String v) { this.finding = v; }
    public String getAction() { return action; }
    public void setAction(String v) { this.action = v; }
    public String getSource() { return source; }
    public void setSource(String v) { this.source = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
