package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Link from a procedure episode to a PCT teleconsult session (tele-PACU / tele-ICU support, Wave 4 §6a).
 * PCT owns the session (session id = PCT referral id); this row holds the ref + purpose.
 */
@Entity
@Table(name = "procedure_teleconsult_link", schema = "inpatient")
public class ProcedureTeleconsultLinkEntity {

    @Id
    @Column(name = "link_id")
    private UUID linkId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "pct_session_id")
    private String pctSessionId;

    @Column(name = "purpose", nullable = false)
    private String purpose = "POSTOP_SUPPORT";

    @Column(name = "session_mode")
    private String sessionMode;

    @Column(name = "status", nullable = false)
    private String status = "REQUESTED";

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "requested_by")
    private String requestedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (linkId == null) linkId = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getLinkId() { return linkId; }
    public void setLinkId(UUID v) { this.linkId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID v) { this.episodeId = v; }
    public String getPctSessionId() { return pctSessionId; }
    public void setPctSessionId(String v) { this.pctSessionId = v; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String v) { this.purpose = v; }
    public String getSessionMode() { return sessionMode; }
    public void setSessionMode(String v) { this.sessionMode = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String v) { this.idempotencyKey = v; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String v) { this.requestedBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
