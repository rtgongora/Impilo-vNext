package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * LINK/PROJECTION row for a NHUME-fulfilled transport leg (patient movement or specimen). NHUME owns
 * the delivery; this row holds the delivery id + a status projection and the SLA-breach (overdue) flag.
 */
@Entity
@Table(name = "procedure_transport", schema = "inpatient")
public class ProcedureTransportEntity {

    @Id
    @Column(name = "transport_id")
    private UUID transportId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "kind", nullable = false)
    private String kind;

    @Column(name = "leg", nullable = false)
    private String leg;

    @Column(name = "seq", nullable = false)
    private int seq = 1;

    @Column(name = "nhume_delivery_id")
    private String nhumeDeliveryId;

    @Column(name = "nhume_delivery_type")
    private String nhumeDeliveryType;

    @Column(name = "oros_specimen_ref")
    private String orosSpecimenRef;

    @Column(name = "status", nullable = false)
    private String status = "REQUESTED";

    @Column(name = "sla_due_at")
    private OffsetDateTime slaDueAt;

    @Column(name = "overdue", nullable = false)
    private boolean overdue = false;

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
        if (transportId == null) transportId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getTransportId() { return transportId; }
    public void setTransportId(UUID v) { this.transportId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID v) { this.episodeId = v; }
    public String getKind() { return kind; }
    public void setKind(String v) { this.kind = v; }
    public String getLeg() { return leg; }
    public void setLeg(String v) { this.leg = v; }
    public int getSeq() { return seq; }
    public void setSeq(int v) { this.seq = v; }
    public String getNhumeDeliveryId() { return nhumeDeliveryId; }
    public void setNhumeDeliveryId(String v) { this.nhumeDeliveryId = v; }
    public String getNhumeDeliveryType() { return nhumeDeliveryType; }
    public void setNhumeDeliveryType(String v) { this.nhumeDeliveryType = v; }
    public String getOrosSpecimenRef() { return orosSpecimenRef; }
    public void setOrosSpecimenRef(String v) { this.orosSpecimenRef = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public OffsetDateTime getSlaDueAt() { return slaDueAt; }
    public void setSlaDueAt(OffsetDateTime v) { this.slaDueAt = v; }
    public boolean isOverdue() { return overdue; }
    public void setOverdue(boolean v) { this.overdue = v; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String v) { this.idempotencyKey = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { this.createdBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
