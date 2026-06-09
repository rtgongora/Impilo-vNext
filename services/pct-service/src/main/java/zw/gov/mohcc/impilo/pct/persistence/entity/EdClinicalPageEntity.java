package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ed_clinical_page")
public class EdClinicalPageEntity {

    @Id
    @Column(name = "page_id")
    private UUID pageId;

    @Column(name = "visit_id", nullable = false)
    private UUID visitId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "page_type", nullable = false)
    private String pageType;

    @Column(name = "urgency", nullable = false)
    private String urgency = "STAT";

    @Column(name = "recipient_role")
    private String recipientRole;

    @Column(name = "recipient_id")
    private String recipientId;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "support_ticket_ref")
    private String supportTicketRef;

    @Column(name = "notification_ref")
    private String notificationRef;

    @Column(name = "sent_by", nullable = false)
    private String sentBy;

    @Column(name = "sent_at", nullable = false)
    private OffsetDateTime sentAt;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @PrePersist
    protected void onCreate() {
        if (pageId == null) pageId = UUID.randomUUID();
        if (sentAt == null) sentAt = OffsetDateTime.now();
    }

    public UUID getPageId() { return pageId; }
    public UUID getVisitId() { return visitId; }
    public void setVisitId(UUID visitId) { this.visitId = visitId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getPageType() { return pageType; }
    public void setPageType(String pageType) { this.pageType = pageType; }
    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }
    public String getRecipientRole() { return recipientRole; }
    public void setRecipientRole(String recipientRole) { this.recipientRole = recipientRole; }
    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSupportTicketRef() { return supportTicketRef; }
    public void setSupportTicketRef(String supportTicketRef) { this.supportTicketRef = supportTicketRef; }
    public String getNotificationRef() { return notificationRef; }
    public void setNotificationRef(String notificationRef) { this.notificationRef = notificationRef; }
    public String getSentBy() { return sentBy; }
    public void setSentBy(String sentBy) { this.sentBy = sentBy; }
    public OffsetDateTime getSentAt() { return sentAt; }
    public OffsetDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(OffsetDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
}
