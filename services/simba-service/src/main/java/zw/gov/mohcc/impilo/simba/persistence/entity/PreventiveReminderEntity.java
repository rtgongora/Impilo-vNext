package zw.gov.mohcc.impilo.simba.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Preventive wellness reminder (screening / follow-up / habit / measurement). Simba schedules and
 * tracks reminder state; Khuluma delivers the notification. When a reminder becomes DUE the scheduler
 * fires it (status FIRED), emits the Khuluma seam event, and records a timeline + integration row.
 */
@Entity
@Table(name = "simba_preventive_reminder", schema = "simba")
public class PreventiveReminderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reminder_id", nullable = false, unique = true)
    private UUID reminderId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "person_cpid", nullable = false, length = 128)
    private String personCpid;

    @Column(name = "programme_id")
    private UUID programmeId;

    @Column(name = "reminder_type", nullable = false, length = 16)
    private String reminderType;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "detail", length = 512)
    private String detail;

    @Column(name = "due_at", nullable = false)
    private OffsetDateTime dueAt;

    @Column(name = "cadence_months")
    private Integer cadenceMonths;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "SCHEDULED";

    @Column(name = "channel", nullable = false, length = 24)
    private String channel = "PUSH";

    @Column(name = "source_ref", length = 128)
    private String sourceRef;

    @Column(name = "last_fired_at")
    private OffsetDateTime lastFiredAt;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (reminderId == null) {
            reminderId = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getReminderId() { return reminderId; }
    public void setReminderId(UUID reminderId) { this.reminderId = reminderId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getPersonCpid() { return personCpid; }
    public void setPersonCpid(String personCpid) { this.personCpid = personCpid; }
    public UUID getProgrammeId() { return programmeId; }
    public void setProgrammeId(UUID programmeId) { this.programmeId = programmeId; }
    public String getReminderType() { return reminderType; }
    public void setReminderType(String reminderType) { this.reminderType = reminderType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public OffsetDateTime getDueAt() { return dueAt; }
    public void setDueAt(OffsetDateTime dueAt) { this.dueAt = dueAt; }
    public Integer getCadenceMonths() { return cadenceMonths; }
    public void setCadenceMonths(Integer cadenceMonths) { this.cadenceMonths = cadenceMonths; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }
    public OffsetDateTime getLastFiredAt() { return lastFiredAt; }
    public void setLastFiredAt(OffsetDateTime lastFiredAt) { this.lastFiredAt = lastFiredAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
