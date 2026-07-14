package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * LINK/PROJECTION row for the theatre blood pipeline. MADI owns the blood order + transfusion episode;
 * this row holds only peer record ids + a status projection re-synced from MADI. Never a blood SoR.
 */
@Entity
@Table(name = "procedure_blood_link", schema = "inpatient")
public class ProcedureBloodLinkEntity {

    @Id
    @Column(name = "link_id")
    private UUID linkId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "kind", nullable = false)
    private String kind = "BLOOD";

    @Column(name = "seq", nullable = false)
    private int seq = 1;

    @Column(name = "madi_order_id")
    private String madiOrderId;

    @Column(name = "madi_transfusion_episode_id")
    private String madiTransfusionEpisodeId;

    @Column(name = "oros_blood_order_id")
    private String orosBloodOrderId;

    @Column(name = "nhume_delivery_id")
    private String nhumeDeliveryId;

    @Column(name = "units_requested")
    private Integer unitsRequested;

    @Column(name = "blood_group")
    private String bloodGroup;

    @Column(name = "component_type")
    private String componentType;

    @Column(name = "required", nullable = false)
    private boolean required = true;

    @Column(name = "reservation_status")
    private String reservationStatus;

    @Column(name = "crossmatch_status")
    private String crossmatchStatus;

    @Column(name = "status", nullable = false)
    private String status = "REQUESTED";

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
        if (linkId == null) linkId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getLinkId() { return linkId; }
    public void setLinkId(UUID v) { this.linkId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID v) { this.episodeId = v; }
    public String getKind() { return kind; }
    public void setKind(String v) { this.kind = v; }
    public int getSeq() { return seq; }
    public void setSeq(int v) { this.seq = v; }
    public String getMadiOrderId() { return madiOrderId; }
    public void setMadiOrderId(String v) { this.madiOrderId = v; }
    public String getMadiTransfusionEpisodeId() { return madiTransfusionEpisodeId; }
    public void setMadiTransfusionEpisodeId(String v) { this.madiTransfusionEpisodeId = v; }
    public String getOrosBloodOrderId() { return orosBloodOrderId; }
    public void setOrosBloodOrderId(String v) { this.orosBloodOrderId = v; }
    public String getNhumeDeliveryId() { return nhumeDeliveryId; }
    public void setNhumeDeliveryId(String v) { this.nhumeDeliveryId = v; }
    public Integer getUnitsRequested() { return unitsRequested; }
    public void setUnitsRequested(Integer v) { this.unitsRequested = v; }
    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String v) { this.bloodGroup = v; }
    public String getComponentType() { return componentType; }
    public void setComponentType(String v) { this.componentType = v; }
    public boolean isRequired() { return required; }
    public void setRequired(boolean v) { this.required = v; }
    public String getReservationStatus() { return reservationStatus; }
    public void setReservationStatus(String v) { this.reservationStatus = v; }
    public String getCrossmatchStatus() { return crossmatchStatus; }
    public void setCrossmatchStatus(String v) { this.crossmatchStatus = v; }
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
