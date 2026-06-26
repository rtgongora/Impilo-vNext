package zw.gov.mohcc.impilo.patientsafety.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** MCAZ-issued request for additional information, dispatched via the Comms Hub. */
@Entity
@Table(name = "ps_follow_up_request")
public class FollowUpRequestEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "case_id", nullable = false)
    private UUID caseId;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "requested_by", length = 255)
    private String requestedBy;
    @Column(name = "request_text", nullable = false, columnDefinition = "TEXT")
    private String requestText;
    @Column(name = "channel_hint", nullable = false, length = 32)
    private String channelHint = "COMMS_HUB";
    @Column(name = "conversation_ref", length = 255)
    private String conversationRef;
    @Column(name = "status", nullable = false, length = 16)
    private String status = "REQUESTED";   // REQUESTED | SENT | RESPONDED | CANCELLED
    @Column(name = "due_date")
    private LocalDate dueDate;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;

    protected FollowUpRequestEntity() {}

    public FollowUpRequestEntity(UUID caseId, UUID tenantId, String requestText) {
        this.id = UUID.randomUUID();
        this.caseId = caseId;
        this.tenantId = tenantId;
        this.requestText = requestText;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getCaseId() { return caseId; }
    public UUID getTenantId() { return tenantId; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String v) { this.requestedBy = v; }
    public String getRequestText() { return requestText; }
    public void setRequestText(String v) { this.requestText = v; }
    public String getChannelHint() { return channelHint; }
    public void setChannelHint(String v) { this.channelHint = v; }
    public String getConversationRef() { return conversationRef; }
    public void setConversationRef(String v) { this.conversationRef = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate v) { this.dueDate = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(OffsetDateTime v) { this.respondedAt = v; }
}
