package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "emergency_action", schema = "inpatient")
public class EmergencyActionEntity {

    @Id
    @Column(name = "action_id")
    private UUID actionId;

    @Column(name = "activation_id", nullable = false)
    private UUID activationId;

    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "performed_by")
    private String performedBy;

    @Column(name = "performed_at")
    private OffsetDateTime performedAt;

    @PrePersist
    void onCreate() {
        if (actionId == null) actionId = UUID.randomUUID();
        if (performedAt == null) performedAt = OffsetDateTime.now();
    }

    public UUID getActionId() { return actionId; }
    public void setActionId(UUID actionId) { this.actionId = actionId; }
    public UUID getActivationId() { return activationId; }
    public void setActivationId(UUID activationId) { this.activationId = activationId; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }
    public OffsetDateTime getPerformedAt() { return performedAt; }
    public void setPerformedAt(OffsetDateTime performedAt) { this.performedAt = performedAt; }
}
