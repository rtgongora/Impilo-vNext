package zw.gov.mohcc.impilo.experience.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reminders")
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(nullable = false)
    private String title;

    @Column(name = "reminder_type", nullable = false)
    private String reminderType;

    private String description;

    @Column(name = "scheduled_at", nullable = false)
    private OffsetDateTime scheduledAt;

    @Column(nullable = false)
    private String recurrence;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "source_type")
    private String sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Reminder() {}

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getPatientId() { return patientId; }
    public String getTitle() { return title; }
    public String getReminderType() { return reminderType; }
    public String getDescription() { return description; }
    public OffsetDateTime getScheduledAt() { return scheduledAt; }
    public String getRecurrence() { return recurrence; }
    public Boolean getEnabled() { return enabled; }
    public String getSourceType() { return sourceType; }
    public UUID getSourceId() { return sourceId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
