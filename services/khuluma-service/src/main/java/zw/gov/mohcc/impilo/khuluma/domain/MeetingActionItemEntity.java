package zw.gov.mohcc.impilo.khuluma.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A follow-up action item captured against a MEETING conversation (the template's
 * FOLLOW_UP post-session action). Lifecycle: OPEN → IN_PROGRESS → DONE | CANCELLED.
 */
@Entity
@Table(name = "khuluma_meeting_action_items")
public class MeetingActionItemEntity {

    @Id
    @Column(name = "action_item_id", nullable = false)
    private UUID actionItemId = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "assignee_id", length = 255)
    private String assigneeId;

    @Column(name = "assignee_display_name", length = 255)
    private String assigneeDisplayName;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "OPEN";

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public MeetingActionItemEntity() {}

    public UUID getActionItemId() { return actionItemId; }
    public void setActionItemId(UUID actionItemId) { this.actionItemId = actionItemId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID conversationId) { this.conversationId = conversationId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAssigneeId() { return assigneeId; }
    public void setAssigneeId(String assigneeId) { this.assigneeId = assigneeId; }

    public String getAssigneeDisplayName() { return assigneeDisplayName; }
    public void setAssigneeDisplayName(String assigneeDisplayName) { this.assigneeDisplayName = assigneeDisplayName; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
