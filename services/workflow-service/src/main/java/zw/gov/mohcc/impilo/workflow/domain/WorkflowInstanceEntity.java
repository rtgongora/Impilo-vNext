package zw.gov.mohcc.impilo.workflow.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "wf_instances")
public class WorkflowInstanceEntity {
    @Id @Column(name = "instance_id") private UUID instanceId;
    @Column(name = "definition_id", nullable = false) private UUID definitionId;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(nullable = false, length = 32) private String status = "CREATED";
    @Column(name = "current_step", length = 128) private String currentStep;
    @Column(name = "context_json", columnDefinition = "TEXT") private String contextJson = "{}";
    @Column(name = "initiator_ref", nullable = false) private String initiatorRef;
    @Column(name = "subject_ref") private String subjectRef;
    @Column(name = "started_at") private OffsetDateTime startedAt;
    @Column(name = "completed_at") private OffsetDateTime completedAt;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected WorkflowInstanceEntity() {}
    public WorkflowInstanceEntity(UUID instanceId, UUID definitionId, UUID tenantId,
                                   String initiatorRef, String subjectRef, String contextJson) {
        this.instanceId = instanceId; this.definitionId = definitionId; this.tenantId = tenantId;
        this.initiatorRef = initiatorRef; this.subjectRef = subjectRef;
        this.contextJson = contextJson != null ? contextJson : "{}";
    }

    public UUID getInstanceId() { return instanceId; }
    public UUID getDefinitionId() { return definitionId; }
    public UUID getTenantId() { return tenantId; }
    public String getStatus() { return status; }
    public void setStatus(String s) { this.status = s; }
    public String getCurrentStep() { return currentStep; }
    public void setCurrentStep(String s) { this.currentStep = s; }
    public String getContextJson() { return contextJson; }
    public void setContextJson(String s) { this.contextJson = s; }
    public String getInitiatorRef() { return initiatorRef; }
    public String getSubjectRef() { return subjectRef; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime t) { this.startedAt = t; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime t) { this.completedAt = t; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime t) { this.updatedAt = t; }
}
