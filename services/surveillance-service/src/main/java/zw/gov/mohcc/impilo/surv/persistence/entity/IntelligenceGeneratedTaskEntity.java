package zw.gov.mohcc.impilo.surv.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "intelligence_generated_task", schema = "surv")
public class IntelligenceGeneratedTaskEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "alert_id", nullable = false)
    private Long alertId;
    @Column(name = "task_ref")
    private String taskRef;
    @Column(name = "status", nullable = false)
    private String status = "DRAFT";
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() { createdAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public Long getAlertId() { return alertId; }
    public void setAlertId(Long alertId) { this.alertId = alertId; }
    public String getTaskRef() { return taskRef; }
    public void setTaskRef(String taskRef) { this.taskRef = taskRef; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
