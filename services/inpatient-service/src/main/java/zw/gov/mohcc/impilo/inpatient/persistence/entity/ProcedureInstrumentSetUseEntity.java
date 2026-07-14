package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * LINK/PROJECTION row for an instrument set issued to a theatre case. TUSO owns the instrument_set
 * registry + CSSD sterilisation lifecycle; this row holds only the TUSO set id + an issue/return
 * projection so the INSTRUMENT_SET readiness gate consumes a real STERILE set and SIGN_OUT returns it.
 */
@Entity
@Table(name = "procedure_instrument_set_use", schema = "inpatient")
public class ProcedureInstrumentSetUseEntity {

    @Id
    @Column(name = "use_id")
    private UUID useId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "seq", nullable = false)
    private int seq = 1;

    @Column(name = "tuso_instrument_set_id", nullable = false)
    private String tusoInstrumentSetId;

    @Column(name = "set_name")
    private String setName;

    @Column(name = "issue_status", nullable = false)
    private String issueStatus = "ISSUED";

    @Column(name = "sterile_until")
    private OffsetDateTime sterileUntil;

    @Column(name = "issued_at")
    private OffsetDateTime issuedAt;

    @Column(name = "returned_at")
    private OffsetDateTime returnedAt;

    @Column(name = "contaminated", nullable = false)
    private boolean contaminated = false;

    @Column(name = "incomplete", nullable = false)
    private boolean incomplete = false;

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
        if (useId == null) useId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getUseId() { return useId; }
    public void setUseId(UUID v) { this.useId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID v) { this.episodeId = v; }
    public int getSeq() { return seq; }
    public void setSeq(int v) { this.seq = v; }
    public String getTusoInstrumentSetId() { return tusoInstrumentSetId; }
    public void setTusoInstrumentSetId(String v) { this.tusoInstrumentSetId = v; }
    public String getSetName() { return setName; }
    public void setSetName(String v) { this.setName = v; }
    public String getIssueStatus() { return issueStatus; }
    public void setIssueStatus(String v) { this.issueStatus = v; }
    public OffsetDateTime getSterileUntil() { return sterileUntil; }
    public void setSterileUntil(OffsetDateTime v) { this.sterileUntil = v; }
    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(OffsetDateTime v) { this.issuedAt = v; }
    public OffsetDateTime getReturnedAt() { return returnedAt; }
    public void setReturnedAt(OffsetDateTime v) { this.returnedAt = v; }
    public boolean isContaminated() { return contaminated; }
    public void setContaminated(boolean v) { this.contaminated = v; }
    public boolean isIncomplete() { return incomplete; }
    public void setIncomplete(boolean v) { this.incomplete = v; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String v) { this.idempotencyKey = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { this.createdBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
