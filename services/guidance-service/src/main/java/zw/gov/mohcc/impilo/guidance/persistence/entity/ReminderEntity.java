package zw.gov.mohcc.impilo.guidance.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reminders", schema = "guidance")
public class ReminderEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false) private String tenantId;
    @Column(name = "actor_id", nullable = false) private String actorId;
    @Column(name = "reminder_type", nullable = false, length = 32) private String reminderType;
    @Column(nullable = false, length = 255) private String title;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "due_date") private LocalDate dueDate;
    @Column(nullable = false, length = 16) private String priority = "MEDIUM";
    @Column(nullable = false, length = 32) private String status = "ACTIVE";
    @Column(name = "dismissed_at") private OffsetDateTime dismissedAt;
    @Column(name = "source_ref", length = 255) private String sourceRef;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;

    protected ReminderEntity() {}

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getActorId() { return actorId; }
    public String getReminderType() { return reminderType; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDate getDueDate() { return dueDate; }
    public String getPriority() { return priority; }
    public String getStatus() { return status; }
}
