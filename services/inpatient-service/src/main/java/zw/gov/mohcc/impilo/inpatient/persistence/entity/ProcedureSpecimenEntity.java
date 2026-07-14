package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * LINK/PROJECTION row for a theatre specimen. OROS owns the lab order/specimen/result; this row holds
 * the peer ids + a status projection. Pending pathology is episode-scoped and survives episode close.
 */
@Entity
@Table(name = "procedure_specimen", schema = "inpatient")
public class ProcedureSpecimenEntity {

    @Id
    @Column(name = "specimen_id")
    private UUID specimenId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "kind", nullable = false)
    private String kind = "SPECIMEN";

    @Column(name = "seq", nullable = false)
    private int seq = 1;

    @Column(name = "specimen_label")
    private String specimenLabel;

    @Column(name = "specimen_type")
    private String specimenType;

    @Column(name = "body_site")
    private String bodySite;

    @Column(name = "oros_order_id")
    private String orosOrderId;

    @Column(name = "oros_specimen_id")
    private String orosSpecimenId;

    @Column(name = "oros_result_id")
    private String orosResultId;

    @Column(name = "transport_id")
    private UUID transportId;

    @Column(name = "status", nullable = false)
    private String status = "PARSED";

    @Column(name = "is_critical", nullable = false)
    private boolean critical = false;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "acknowledged_by")
    private String acknowledgedBy;

    @Column(name = "butano_document_ref")
    private String butanoDocumentRef;

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
        if (specimenId == null) specimenId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getSpecimenId() { return specimenId; }
    public void setSpecimenId(UUID v) { this.specimenId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID v) { this.episodeId = v; }
    public String getKind() { return kind; }
    public void setKind(String v) { this.kind = v; }
    public int getSeq() { return seq; }
    public void setSeq(int v) { this.seq = v; }
    public String getSpecimenLabel() { return specimenLabel; }
    public void setSpecimenLabel(String v) { this.specimenLabel = v; }
    public String getSpecimenType() { return specimenType; }
    public void setSpecimenType(String v) { this.specimenType = v; }
    public String getBodySite() { return bodySite; }
    public void setBodySite(String v) { this.bodySite = v; }
    public String getOrosOrderId() { return orosOrderId; }
    public void setOrosOrderId(String v) { this.orosOrderId = v; }
    public String getOrosSpecimenId() { return orosSpecimenId; }
    public void setOrosSpecimenId(String v) { this.orosSpecimenId = v; }
    public String getOrosResultId() { return orosResultId; }
    public void setOrosResultId(String v) { this.orosResultId = v; }
    public UUID getTransportId() { return transportId; }
    public void setTransportId(UUID v) { this.transportId = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public boolean isCritical() { return critical; }
    public void setCritical(boolean v) { this.critical = v; }
    public OffsetDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(OffsetDateTime v) { this.acknowledgedAt = v; }
    public String getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(String v) { this.acknowledgedBy = v; }
    public String getButanoDocumentRef() { return butanoDocumentRef; }
    public void setButanoDocumentRef(String v) { this.butanoDocumentRef = v; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String v) { this.idempotencyKey = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { this.createdBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
