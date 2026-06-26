package zw.gov.mohcc.impilo.patientsafety.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** An auditable action on a safety case (forms the case timeline). */
@Entity
@Table(name = "ps_case_action")
public class CaseActionEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "case_id", nullable = false)
    private UUID caseId;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "action_type", nullable = false, length = 48)
    private String actionType;
    @Column(name = "actor_id", length = 255)
    private String actorId;
    @Column(name = "actor_role", length = 128)
    private String actorRole;
    @Column(name = "from_status", length = 32)
    private String fromStatus;
    @Column(name = "to_status", length = 32)
    private String toStatus;
    @Column(name = "note")
    private String note;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected CaseActionEntity() {}

    public CaseActionEntity(UUID caseId, UUID tenantId, String actionType) {
        this.id = UUID.randomUUID();
        this.caseId = caseId;
        this.tenantId = tenantId;
        this.actionType = actionType;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getCaseId() { return caseId; }
    public UUID getTenantId() { return tenantId; }
    public String getActionType() { return actionType; }
    public String getActorId() { return actorId; }
    public void setActorId(String v) { this.actorId = v; }
    public String getActorRole() { return actorRole; }
    public void setActorRole(String v) { this.actorRole = v; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String v) { this.fromStatus = v; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String v) { this.toStatus = v; }
    public String getNote() { return note; }
    public void setNote(String v) { this.note = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
