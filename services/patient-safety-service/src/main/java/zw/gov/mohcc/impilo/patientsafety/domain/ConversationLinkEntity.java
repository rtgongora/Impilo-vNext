package zw.gov.mohcc.impilo.patientsafety.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * patient-safety's OWN linkage to a Comms Hub (channels-service) conversation. The conversation
 * itself is owned by channels-service; this row only records the reference and purpose so a case
 * can be tied to its follow-up thread without duplicating conversation state.
 */
@Entity
@Table(name = "ps_conversation_link")
public class ConversationLinkEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "case_id", nullable = false)
    private UUID caseId;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "conversation_ref", nullable = false, length = 255)
    private String conversationRef;
    @Column(name = "channel_type", length = 48)
    private String channelType;
    @Column(name = "purpose", nullable = false, length = 32)
    private String purpose = "FOLLOW_UP";
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ConversationLinkEntity() {}

    public ConversationLinkEntity(UUID caseId, UUID tenantId, String conversationRef, String purpose) {
        this.id = UUID.randomUUID();
        this.caseId = caseId;
        this.tenantId = tenantId;
        this.conversationRef = conversationRef;
        if (purpose != null && !purpose.isBlank()) this.purpose = purpose;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getCaseId() { return caseId; }
    public UUID getTenantId() { return tenantId; }
    public String getConversationRef() { return conversationRef; }
    public String getChannelType() { return channelType; }
    public void setChannelType(String v) { this.channelType = v; }
    public String getPurpose() { return purpose; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
