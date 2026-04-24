package zw.gov.mohcc.impilo.tshepo.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "patient_share_policy_rule", schema = "tshepo")
public class PatientSharePolicyRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_action", nullable = false, length = 64)
    private String workflowAction;

    @Column(name = "scope_pattern", nullable = false, length = 64)
    private String scopePattern = "*";

    @Column(name = "actor_pattern", nullable = false, length = 50)
    private String actorPattern = "*";

    @Column(name = "min_trust_level", length = 64)
    private String minTrustLevel;

    @Column(name = "policy_outcome", nullable = false, length = 20)
    private String policyOutcome;

    @Column(name = "permit_read", nullable = false)
    private boolean permitRead;

    @Column(name = "permit_write", nullable = false)
    private boolean permitWrite;

    @Column(name = "permit_temp_provider_id", nullable = false)
    private boolean permitTempProviderId;

    @Column(name = "require_council_registration", nullable = false)
    private boolean requireCouncilRegistration;

    @Column(name = "notes")
    private String notes;

    @Column(name = "active_flag", nullable = false)
    private boolean activeFlag = true;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getWorkflowAction() {
        return workflowAction;
    }

    public String getScopePattern() {
        return scopePattern;
    }

    public String getActorPattern() {
        return actorPattern;
    }

    public String getMinTrustLevel() {
        return minTrustLevel;
    }

    public String getPolicyOutcome() {
        return policyOutcome;
    }

    public boolean isPermitRead() {
        return permitRead;
    }

    public boolean isPermitWrite() {
        return permitWrite;
    }

    public boolean isPermitTempProviderId() {
        return permitTempProviderId;
    }

    public boolean isRequireCouncilRegistration() {
        return requireCouncilRegistration;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isActiveFlag() {
        return activeFlag;
    }

    public int getPriority() {
        return priority;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setWorkflowAction(String workflowAction) {
        this.workflowAction = workflowAction;
    }

    public void setScopePattern(String scopePattern) {
        this.scopePattern = scopePattern;
    }

    public void setActorPattern(String actorPattern) {
        this.actorPattern = actorPattern;
    }

    public void setMinTrustLevel(String minTrustLevel) {
        this.minTrustLevel = minTrustLevel;
    }

    public void setPolicyOutcome(String policyOutcome) {
        this.policyOutcome = policyOutcome;
    }

    public void setPermitRead(boolean permitRead) {
        this.permitRead = permitRead;
    }

    public void setPermitWrite(boolean permitWrite) {
        this.permitWrite = permitWrite;
    }

    public void setPermitTempProviderId(boolean permitTempProviderId) {
        this.permitTempProviderId = permitTempProviderId;
    }

    public void setRequireCouncilRegistration(boolean requireCouncilRegistration) {
        this.requireCouncilRegistration = requireCouncilRegistration;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public void setActiveFlag(boolean activeFlag) {
        this.activeFlag = activeFlag;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }
}
