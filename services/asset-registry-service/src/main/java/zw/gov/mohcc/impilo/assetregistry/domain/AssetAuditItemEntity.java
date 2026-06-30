package zw.gov.mohcc.impilo.assetregistry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** A single expected/confirmed/exception line within an asset audit. */
@Entity
@Table(name = "asr_asset_audit_item")
public class AssetAuditItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "audit_item_id")
    private UUID auditItemId;

    @Column(name = "audit_id", nullable = false)
    private UUID auditId;

    @Column(name = "equipment_id")
    private UUID equipmentId;

    @Column(name = "expected", nullable = false)
    private boolean expected = true;

    @Column(name = "confirmed", nullable = false)
    private boolean confirmed = false;

    @Column(name = "exception_code", length = 32)
    private String exceptionCode;

    @Column(name = "note", length = 512)
    private String note;

    protected AssetAuditItemEntity() {}

    public AssetAuditItemEntity(UUID auditId, UUID equipmentId) {
        this.auditId = auditId;
        this.equipmentId = equipmentId;
    }

    public UUID getAuditItemId() { return auditItemId; }
    public UUID getAuditId() { return auditId; }
    public UUID getEquipmentId() { return equipmentId; }
    public void setEquipmentId(UUID equipmentId) { this.equipmentId = equipmentId; }
    public boolean isExpected() { return expected; }
    public void setExpected(boolean expected) { this.expected = expected; }
    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }
    public String getExceptionCode() { return exceptionCode; }
    public void setExceptionCode(String exceptionCode) { this.exceptionCode = exceptionCode; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
