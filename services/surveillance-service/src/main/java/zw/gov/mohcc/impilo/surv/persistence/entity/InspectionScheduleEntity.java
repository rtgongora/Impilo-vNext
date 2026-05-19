package zw.gov.mohcc.impilo.surv.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inspection_schedule", schema = "surv")
public class InspectionScheduleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "site_id", nullable = false)
    private UUID siteId;
    @Column(name = "inspection_type", nullable = false)
    private String inspectionType;
    @Column(name = "scheduled_at", nullable = false)
    private OffsetDateTime scheduledAt;
    @Column(name = "responsible_ref")
    private String responsibleRef;
    @Column(name = "priority")
    private String priority;
    @Column(name = "reason")
    private String reason;
    @Column(name = "scope_ref")
    private String scopeRef;
    @Column(name = "recurrence_rule")
    private String recurrenceRule;
    @Column(name = "notes")
    private String notes;
    @Column(name = "notification_preference")
    private String notificationPreference;
    @Column(name = "status")
    private String status = "SCHEDULED";
    @Column(name = "created_by", nullable = false)
    private String createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getSiteId() { return siteId; }
    public void setSiteId(UUID siteId) { this.siteId = siteId; }
    public String getInspectionType() { return inspectionType; }
    public void setInspectionType(String inspectionType) { this.inspectionType = inspectionType; }
    public OffsetDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(OffsetDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public String getResponsibleRef() { return responsibleRef; }
    public void setResponsibleRef(String responsibleRef) { this.responsibleRef = responsibleRef; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getScopeRef() { return scopeRef; }
    public void setScopeRef(String scopeRef) { this.scopeRef = scopeRef; }
    public String getRecurrenceRule() { return recurrenceRule; }
    public void setRecurrenceRule(String recurrenceRule) { this.recurrenceRule = recurrenceRule; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getNotificationPreference() { return notificationPreference; }
    public void setNotificationPreference(String notificationPreference) { this.notificationPreference = notificationPreference; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
