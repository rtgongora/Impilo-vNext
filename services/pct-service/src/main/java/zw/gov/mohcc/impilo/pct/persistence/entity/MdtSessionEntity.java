package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * TM-B15 (B15-1): a chaired MDT / specialist-board session over a case agenda (V051).
 * The board outcome lives on the case items; this row carries governance: chair/scribe,
 * panel pool, and the identity-visibility policy enforced server-side at the read model.
 */
@Entity
@Table(name = "pct_mdt_sessions")
public class MdtSessionEntity {

    @Id
    @Column(name = "mdt_session_id")
    private UUID mdtSessionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "title")
    private String title;

    @Column(name = "chair_id", nullable = false)
    private String chairId;

    @Column(name = "scribe_id")
    private String scribeId;

    @Column(name = "specialty")
    private String specialty;

    @Column(name = "routing_pool_id")
    private String routingPoolId;

    @Column(name = "rtc_session_id")
    private String rtcSessionId;

    @Column(name = "identity_visibility", nullable = false)
    private String identityVisibility = "PSEUDONYMISED";

    @Column(name = "status", nullable = false)
    private String status = "PLANNED";

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getMdtSessionId() { return mdtSessionId; }
    public void setMdtSessionId(UUID mdtSessionId) { this.mdtSessionId = mdtSessionId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getChairId() { return chairId; }
    public void setChairId(String chairId) { this.chairId = chairId; }
    public String getScribeId() { return scribeId; }
    public void setScribeId(String scribeId) { this.scribeId = scribeId; }
    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }
    public String getRoutingPoolId() { return routingPoolId; }
    public void setRoutingPoolId(String routingPoolId) { this.routingPoolId = routingPoolId; }
    public String getRtcSessionId() { return rtcSessionId; }
    public void setRtcSessionId(String rtcSessionId) { this.rtcSessionId = rtcSessionId; }
    public String getIdentityVisibility() { return identityVisibility; }
    public void setIdentityVisibility(String identityVisibility) { this.identityVisibility = identityVisibility; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(OffsetDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
