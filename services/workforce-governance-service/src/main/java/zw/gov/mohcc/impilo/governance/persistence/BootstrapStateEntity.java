package zw.gov.mohcc.impilo.governance.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wgv_bootstrap_state")
public class BootstrapStateEntity {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "bootstrap_open", nullable = false)
    private boolean bootstrapOpen = true;
    @Column(name = "bootstrap_closed_at")
    private Instant bootstrapClosedAt;
    @Column(name = "recovery_mode", nullable = false)
    private boolean recoveryMode = false;
    @Column(name = "recovery_opened_at")
    private Instant recoveryOpenedAt;
    @Column(name = "recovery_closed_at")
    private Instant recoveryClosedAt;
    @Column(name = "active_national_admin_user_id")
    private String activeNationalAdminUserId;
    @Column(name = "bootstrap_account_id")
    private UUID bootstrapAccountId;
    @Column(name = "bootstrap_method")
    private String bootstrapMethod;
    @Column(name = "bootstrap_role_expires_at")
    private Instant bootstrapRoleExpiresAt;
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BootstrapStateEntity() {}

    public void init(UUID tenantId) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean isBootstrapOpen() { return bootstrapOpen; }
    public void setBootstrapOpen(boolean bootstrapOpen) { this.bootstrapOpen = bootstrapOpen; touch(); }
    public Instant getBootstrapClosedAt() { return bootstrapClosedAt; }
    public void setBootstrapClosedAt(Instant bootstrapClosedAt) { this.bootstrapClosedAt = bootstrapClosedAt; touch(); }
    public boolean isRecoveryMode() { return recoveryMode; }
    public void setRecoveryMode(boolean recoveryMode) { this.recoveryMode = recoveryMode; touch(); }
    public Instant getRecoveryOpenedAt() { return recoveryOpenedAt; }
    public void setRecoveryOpenedAt(Instant recoveryOpenedAt) { this.recoveryOpenedAt = recoveryOpenedAt; touch(); }
    public Instant getRecoveryClosedAt() { return recoveryClosedAt; }
    public void setRecoveryClosedAt(Instant recoveryClosedAt) { this.recoveryClosedAt = recoveryClosedAt; touch(); }
    public String getActiveNationalAdminUserId() { return activeNationalAdminUserId; }
    public void setActiveNationalAdminUserId(String activeNationalAdminUserId) { this.activeNationalAdminUserId = activeNationalAdminUserId; touch(); }
    public UUID getBootstrapAccountId() { return bootstrapAccountId; }
    public void setBootstrapAccountId(UUID bootstrapAccountId) { this.bootstrapAccountId = bootstrapAccountId; touch(); }
    public void setMetadata(String metadata) { this.metadata = metadata; touch(); }

    private void touch() { this.updatedAt = Instant.now(); }
}
