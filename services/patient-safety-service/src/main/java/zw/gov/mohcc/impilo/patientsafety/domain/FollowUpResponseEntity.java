package zw.gov.mohcc.impilo.patientsafety.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A reporter's response to a follow-up request; attaches to the case. */
@Entity
@Table(name = "ps_follow_up_response")
public class FollowUpResponseEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "follow_up_request_id", nullable = false)
    private UUID followUpRequestId;
    @Column(name = "case_id", nullable = false)
    private UUID caseId;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "responder_id", length = 255)
    private String responderId;
    @Column(name = "responder_kind", length = 16)
    private String responderKind;
    @Column(name = "response_text", nullable = false)
    private String responseText;
    @Column(name = "attachment_object_id", length = 255)
    private String attachmentObjectId;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected FollowUpResponseEntity() {}

    public FollowUpResponseEntity(UUID followUpRequestId, UUID caseId, UUID tenantId, String responseText) {
        this.id = UUID.randomUUID();
        this.followUpRequestId = followUpRequestId;
        this.caseId = caseId;
        this.tenantId = tenantId;
        this.responseText = responseText;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getFollowUpRequestId() { return followUpRequestId; }
    public UUID getCaseId() { return caseId; }
    public UUID getTenantId() { return tenantId; }
    public String getResponderId() { return responderId; }
    public void setResponderId(String v) { this.responderId = v; }
    public String getResponderKind() { return responderKind; }
    public void setResponderKind(String v) { this.responderKind = v; }
    public String getResponseText() { return responseText; }
    public void setResponseText(String v) { this.responseText = v; }
    public String getAttachmentObjectId() { return attachmentObjectId; }
    public void setAttachmentObjectId(String v) { this.attachmentObjectId = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
