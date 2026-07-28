package zw.gov.mohcc.impilo.support.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The closed, positive vocabulary of what a support team's assignments
 * permit. Absence from this table means the action is not permitted —
 * support access is enumerated, never implied by a role name.
 */
@Entity
@Table(name = "sup_support_permitted_action")
public class SupportPermittedActionEntity {
    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "team_id", nullable = false) private UUID teamId;
    @Column(name = "action_code", nullable = false, length = 128) private String actionCode;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;

    protected SupportPermittedActionEntity() {}

    public SupportPermittedActionEntity(UUID id, UUID tenantId, UUID teamId, String actionCode) {
        this.id = id;
        this.tenantId = tenantId;
        this.teamId = teamId;
        this.actionCode = actionCode;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getTeamId() { return teamId; }
    public String getActionCode() { return actionCode; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
