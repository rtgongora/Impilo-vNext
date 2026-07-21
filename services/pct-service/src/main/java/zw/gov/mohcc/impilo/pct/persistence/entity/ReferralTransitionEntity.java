package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Append-only ledger of referral state transitions (TM-B1).
 *
 * <p>Every attempted transition writes one row — whether allowed or (in SHADOW)
 * a flagged violation. It serves three purposes: the §11.3 per-transition audit
 * record, the shadow→enforce flip criterion ({@code allowed = false} count over a
 * window), and the provenance substrate for task-driven transitions (TM-B7).</p>
 */
@Entity
@Table(name = "pct_referral_transitions")
public class ReferralTransitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "referral_id", nullable = false)
    private UUID referralId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Nullable: a legacy/unknown source status resolves to null rather than failing. */
    @Column(name = "from_status")
    private String fromStatus;

    @Column(name = "to_status", nullable = false)
    private String toStatus;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "actor")
    private String actor;

    @Column(name = "allowed", nullable = false)
    private boolean allowed;

    @Column(name = "mode", nullable = false)
    private String mode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public UUID getReferralId() { return referralId; }
    public void setReferralId(UUID referralId) { this.referralId = referralId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public boolean isAllowed() { return allowed; }
    public void setAllowed(boolean allowed) { this.allowed = allowed; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
