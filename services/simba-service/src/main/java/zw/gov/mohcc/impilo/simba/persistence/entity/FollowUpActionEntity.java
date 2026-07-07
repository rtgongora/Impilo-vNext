package zw.gov.mohcc.impilo.simba.persistence.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Provider wellness follow-up action, created from a touchpoint, care-linkage, alert review, or
 * assessment. Distinct from a clinical task — the clinical workflow stays with PCT. Simba tracks the
 * wellness follow-up lifecycle (OPEN → IN_PROGRESS → DONE/CANCELLED).
 */
@Entity
@Table(name = "simba_follow_up_action", schema = "simba")
public class FollowUpActionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "follow_up_id", nullable = false, unique = true)
    private UUID followUpId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "person_cpid", nullable = false, length = 128)
    private String personCpid;

    @Column(name = "created_by_provider", length = 128)
    private String createdByProvider;

    @Column(name = "origin_type", nullable = false, length = 24)
    private String originType;

    @Column(name = "origin_ref", length = 128)
    private String originRef;

    @Column(name = "action_type", nullable = false, length = 48)
    private String actionType;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "detail", length = 512)
    private String detail;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "OPEN";

    @Column(name = "severity", nullable = false, length = 16)
    private String severity = "ROUTINE";

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (followUpId == null) {
            followUpId = UUID.randomUUID();
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
    public UUID getFollowUpId() { return followUpId; }
    public void setFollowUpId(UUID followUpId) { this.followUpId = followUpId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getPersonCpid() { return personCpid; }
    public void setPersonCpid(String personCpid) { this.personCpid = personCpid; }
    public String getCreatedByProvider() { return createdByProvider; }
    public void setCreatedByProvider(String createdByProvider) { this.createdByProvider = createdByProvider; }
    public String getOriginType() { return originType; }
    public void setOriginType(String originType) { this.originType = originType; }
    public String getOriginRef() { return originRef; }
    public void setOriginRef(String originRef) { this.originRef = originRef; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
