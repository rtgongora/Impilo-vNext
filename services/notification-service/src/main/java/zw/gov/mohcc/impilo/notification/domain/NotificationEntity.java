package zw.gov.mohcc.impilo.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ns_notifications")
public class NotificationEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "template_key", length = 128)
    private String templateKey;

    @Column(name = "channel", length = 32, nullable = false)
    private String channel;

    @Column(name = "to_addr", length = 256, nullable = false)
    private String toAddr;

    @Column(name = "inbox_recipient", length = 256)
    private String inboxRecipient;

    @Column(name = "patient_ref", length = 300)
    private String patientRef;

    @Column(name = "vars_json", columnDefinition = "TEXT")
    private String varsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "pod_id", length = 64, nullable = false)
    private String podId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    // --- Getters and Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTemplateKey() { return templateKey; }
    public void setTemplateKey(String templateKey) { this.templateKey = templateKey; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getToAddr() { return toAddr; }
    public void setToAddr(String toAddr) { this.toAddr = toAddr; }

    public String getInboxRecipient() { return inboxRecipient; }
    public void setInboxRecipient(String inboxRecipient) { this.inboxRecipient = inboxRecipient; }

    public String getPatientRef() { return patientRef; }
    public void setPatientRef(String patientRef) { this.patientRef = patientRef; }

    public String getVarsJson() { return varsJson; }
    public void setVarsJson(String varsJson) { this.varsJson = varsJson; }

    public NotificationStatus getStatus() { return status; }
    public void setStatus(NotificationStatus status) { this.status = status; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getPodId() { return podId; }
    public void setPodId(String podId) { this.podId = podId; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getSentAt() { return sentAt; }
    public void setSentAt(OffsetDateTime sentAt) { this.sentAt = sentAt; }

    public OffsetDateTime getReadAt() { return readAt; }
    public void setReadAt(OffsetDateTime readAt) { this.readAt = readAt; }
}
