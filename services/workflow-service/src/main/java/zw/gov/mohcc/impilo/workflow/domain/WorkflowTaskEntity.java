package zw.gov.mohcc.impilo.workflow.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "wf_tasks")
public class WorkflowTaskEntity {
    @Id @Column(name = "task_id") private UUID taskId;
    @Column(name = "instance_id", nullable = false) private UUID instanceId;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "step_name", nullable = false, length = 128) private String stepName;
    @Column(nullable = false, length = 32) private String status = "PENDING";
    @Column(name = "assignee_ref") private String assigneeRef;
    @Column(name = "input_json", columnDefinition = "TEXT") private String inputJson;
    @Column(name = "output_json", columnDefinition = "TEXT") private String outputJson;
    @Column(name = "started_at") private OffsetDateTime startedAt;
    @Column(name = "completed_at") private OffsetDateTime completedAt;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected WorkflowTaskEntity() {}
    public WorkflowTaskEntity(UUID taskId, UUID instanceId, UUID tenantId, String stepName,
                               String assigneeRef, String inputJson) {
        this.taskId = taskId; this.instanceId = instanceId; this.tenantId = tenantId;
        this.stepName = stepName; this.assigneeRef = assigneeRef; this.inputJson = inputJson;
    }

    public UUID getTaskId() { return taskId; }
    public UUID getInstanceId() { return instanceId; }
    public UUID getTenantId() { return tenantId; }
    public String getStepName() { return stepName; }
    public String getStatus() { return status; }
    public void setStatus(String s) { this.status = s; }
    public String getAssigneeRef() { return assigneeRef; }
    public void setAssigneeRef(String s) { this.assigneeRef = s; }
    public String getInputJson() { return inputJson; }
    public String getOutputJson() { return outputJson; }
    public void setOutputJson(String s) { this.outputJson = s; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime t) { this.startedAt = t; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime t) { this.completedAt = t; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime t) { this.updatedAt = t; }
}
