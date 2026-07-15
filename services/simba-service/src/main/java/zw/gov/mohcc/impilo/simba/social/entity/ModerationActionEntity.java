package zw.gov.mohcc.impilo.simba.social.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A moderation action taken on social content (state machine + rationale). */
@Entity
@Table(name = "simba_moderation_action", schema = "simba")
public class ModerationActionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "action_id", nullable = false, unique = true)
    private UUID actionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "report_id")
    private UUID reportId;

    @Column(name = "subject_type", nullable = false, length = 16)
    private String subjectType;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "moderator_cpid", length = 128)
    private String moderatorCpid;

    @Column(name = "action_state", nullable = false, length = 16)
    private String actionState;

    @Column(name = "rationale", length = 512)
    private String rationale;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (actionId == null) {
            actionId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getActionId() { return actionId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getReportId() { return reportId; }
    public void setReportId(UUID reportId) { this.reportId = reportId; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public UUID getSubjectId() { return subjectId; }
    public void setSubjectId(UUID subjectId) { this.subjectId = subjectId; }
    public String getModeratorCpid() { return moderatorCpid; }
    public void setModeratorCpid(String moderatorCpid) { this.moderatorCpid = moderatorCpid; }
    public String getActionState() { return actionState; }
    public void setActionState(String actionState) { this.actionState = actionState; }
    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
