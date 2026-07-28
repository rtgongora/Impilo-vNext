package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * What a clinician DID about a diagnostic result (brief.md §11, §25).
 *
 * <p>OROS owns the order, the result and the acknowledgement — that somebody saw it. This owns what
 * it caused. A result can be acknowledged and change nothing, and those are different facts.</p>
 */
@Entity
@Table(name = "pct_result_actions")
public class ResultActionEntity {

    @Id
    @Column(name = "action_id")
    private UUID actionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_cpid", nullable = false)
    private String subjectCpid;

    @Column(name = "journey_id")
    private UUID journeyId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    /** Typed reference into OROS. No FK — an FK can only name a local table. */
    @Column(name = "result_ref", nullable = false)
    private String resultRef;

    @Column(name = "order_ref")
    private String orderRef;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "rationale")
    private String rationale;

    @Column(name = "problem_id")
    private UUID problemId;

    @Column(name = "actioned_by", nullable = false)
    private String actionedBy;

    @Column(name = "actioned_at", nullable = false)
    private OffsetDateTime actionedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (actionId == null) actionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (actionedAt == null) actionedAt = now;
    }

    public UUID getActionId() { return actionId; }
    public void setActionId(UUID v) { this.actionId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { this.subjectCpid = v; }
    public UUID getJourneyId() { return journeyId; }
    public void setJourneyId(UUID v) { this.journeyId = v; }
    public UUID getEncounterId() { return encounterId; }
    public void setEncounterId(UUID v) { this.encounterId = v; }
    public String getResultRef() { return resultRef; }
    public void setResultRef(String v) { this.resultRef = v; }
    public String getOrderRef() { return orderRef; }
    public void setOrderRef(String v) { this.orderRef = v; }
    public String getAction() { return action; }
    public void setAction(String v) { this.action = v; }
    public String getRationale() { return rationale; }
    public void setRationale(String v) { this.rationale = v; }
    public UUID getProblemId() { return problemId; }
    public void setProblemId(UUID v) { this.problemId = v; }
    public String getActionedBy() { return actionedBy; }
    public void setActionedBy(String v) { this.actionedBy = v; }
    public OffsetDateTime getActionedAt() { return actionedAt; }
    public void setActionedAt(OffsetDateTime v) { this.actionedAt = v; }
}
