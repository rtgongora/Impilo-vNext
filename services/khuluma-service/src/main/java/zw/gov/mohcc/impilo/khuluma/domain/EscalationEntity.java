package zw.gov.mohcc.impilo.khuluma.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A khuluma escalation and its SLA (Phase 5 / W4, G-KH-01).
 * Lifecycle: OPEN → ASSIGNED → ACCEPTED → RESOLVED (BREACHED / CANCELLED are also terminal-ish).
 */
@Entity
@Table(name = "khuluma_escalation")
public class EscalationEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "conversation_id") private UUID conversationId;
    @Column(columnDefinition = "TEXT") private String reason;
    @Column(nullable = false, length = 16) private String priority = "MEDIUM";
    @Column(nullable = false) private int tier = 1;
    @Column(nullable = false, length = 16) private String status = "OPEN";
    @Column(name = "assigned_to") private String assignedTo;
    @Column(name = "first_response_due_at") private OffsetDateTime firstResponseDueAt;
    @Column(name = "resolution_due_at") private OffsetDateTime resolutionDueAt;
    @Column(name = "first_responded_at") private OffsetDateTime firstRespondedAt;
    @Column(name = "resolved_at") private OffsetDateTime resolvedAt;
    @Column(name = "first_response_breached", nullable = false) private boolean firstResponseBreached = false;
    @Column(name = "resolution_breached", nullable = false) private boolean resolutionBreached = false;
    @Column(name = "resolution_note", columnDefinition = "TEXT") private String resolutionNote;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    @PrePersist void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID v) { this.conversationId = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public String getPriority() { return priority; }
    public void setPriority(String v) { this.priority = v; }
    public int getTier() { return tier; }
    public void setTier(int v) { this.tier = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String v) { this.assignedTo = v; }
    public OffsetDateTime getFirstResponseDueAt() { return firstResponseDueAt; }
    public void setFirstResponseDueAt(OffsetDateTime v) { this.firstResponseDueAt = v; }
    public OffsetDateTime getResolutionDueAt() { return resolutionDueAt; }
    public void setResolutionDueAt(OffsetDateTime v) { this.resolutionDueAt = v; }
    public OffsetDateTime getFirstRespondedAt() { return firstRespondedAt; }
    public void setFirstRespondedAt(OffsetDateTime v) { this.firstRespondedAt = v; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime v) { this.resolvedAt = v; }
    public boolean isFirstResponseBreached() { return firstResponseBreached; }
    public void setFirstResponseBreached(boolean v) { this.firstResponseBreached = v; }
    public boolean isResolutionBreached() { return resolutionBreached; }
    public void setResolutionBreached(boolean v) { this.resolutionBreached = v; }
    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String v) { this.resolutionNote = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
