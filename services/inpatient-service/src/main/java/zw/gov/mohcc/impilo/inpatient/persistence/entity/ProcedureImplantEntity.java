package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * LINK/PROJECTION row for a surgical implant. inventory-service (DURA) owns the implant UNIT registry
 * (UDI/serial/lot) + the patient-implant link; this row holds only the peer ids + a UDI/serial/lot
 * projection so a recalled UDI/lot can be traced from the case side. Never an implant SoR.
 */
@Entity
@Table(name = "procedure_implant", schema = "inpatient")
public class ProcedureImplantEntity {

    @Id
    @Column(name = "implant_id")
    private UUID implantId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "seq", nullable = false)
    private int seq = 1;

    @Column(name = "inventory_implant_unit_id")
    private String inventoryImplantUnitId;

    @Column(name = "inventory_patient_implant_id")
    private String inventoryPatientImplantId;

    @Column(name = "udi")
    private String udi;

    @Column(name = "serial_number")
    private String serialNumber;

    @Column(name = "lot_number")
    private String lotNumber;

    @Column(name = "device_type")
    private String deviceType;

    @Column(name = "body_site")
    private String bodySite;

    @Column(name = "laterality")
    private String laterality;

    @Column(name = "implanted_by")
    private String implantedBy;

    @Column(name = "implanted_at")
    private OffsetDateTime implantedAt;

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
        if (implantId == null) implantId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getImplantId() { return implantId; }
    public void setImplantId(UUID v) { this.implantId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID v) { this.episodeId = v; }
    public int getSeq() { return seq; }
    public void setSeq(int v) { this.seq = v; }
    public String getInventoryImplantUnitId() { return inventoryImplantUnitId; }
    public void setInventoryImplantUnitId(String v) { this.inventoryImplantUnitId = v; }
    public String getInventoryPatientImplantId() { return inventoryPatientImplantId; }
    public void setInventoryPatientImplantId(String v) { this.inventoryPatientImplantId = v; }
    public String getUdi() { return udi; }
    public void setUdi(String v) { this.udi = v; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String v) { this.serialNumber = v; }
    public String getLotNumber() { return lotNumber; }
    public void setLotNumber(String v) { this.lotNumber = v; }
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String v) { this.deviceType = v; }
    public String getBodySite() { return bodySite; }
    public void setBodySite(String v) { this.bodySite = v; }
    public String getLaterality() { return laterality; }
    public void setLaterality(String v) { this.laterality = v; }
    public String getImplantedBy() { return implantedBy; }
    public void setImplantedBy(String v) { this.implantedBy = v; }
    public OffsetDateTime getImplantedAt() { return implantedAt; }
    public void setImplantedAt(OffsetDateTime v) { this.implantedAt = v; }
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
