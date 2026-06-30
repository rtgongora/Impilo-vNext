package zw.gov.mohcc.impilo.simba.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Ordered stage/task within a wellness plan template. */
@Entity
@Table(name = "plan_tasks", schema = "simba")
public class PlanTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_task_id", nullable = false, unique = true)
    private UUID planTaskId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "stage", nullable = false)
    private Integer stage = 1;

    @Column(name = "seq", nullable = false)
    private Integer seq = 1;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "task_type", nullable = false, length = 32)
    private String taskType = "HABIT";

    @Column(name = "fundo_content_ref", length = 128)
    private String fundoContentRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (planTaskId == null) {
            planTaskId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getPlanTaskId() { return planTaskId; }
    public void setPlanTaskId(UUID planTaskId) { this.planTaskId = planTaskId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }
    public Integer getStage() { return stage; }
    public void setStage(Integer stage) { this.stage = stage; }
    public Integer getSeq() { return seq; }
    public void setSeq(Integer seq) { this.seq = seq; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getFundoContentRef() { return fundoContentRef; }
    public void setFundoContentRef(String fundoContentRef) { this.fundoContentRef = fundoContentRef; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
