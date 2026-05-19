package zw.gov.mohcc.impilo.integration.governance.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ih_external_event_subscriptions")
public class ExternalEventSubscriptionEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "external_app_id", length = 36, nullable = false)
    private String externalAppId;

    @Column(name = "integration_contract_id", length = 36, nullable = false)
    private String integrationContractId;

    @Column(name = "webhook_subscription_id", length = 36, nullable = false)
    private String webhookSubscriptionId;

    @Column(name = "event_topic", length = 256, nullable = false)
    private String eventTopic;

    @Column(name = "status", length = 16, nullable = false)
    private String status = "ACTIVE";

    @Column(name = "approved_by", length = 64)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "pod_id", length = 64, nullable = false)
    private String podId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getExternalAppId() { return externalAppId; }
    public void setExternalAppId(String v) { this.externalAppId = v; }
    public String getIntegrationContractId() { return integrationContractId; }
    public void setIntegrationContractId(String v) { this.integrationContractId = v; }
    public String getWebhookSubscriptionId() { return webhookSubscriptionId; }
    public void setWebhookSubscriptionId(String v) { this.webhookSubscriptionId = v; }
    public String getEventTopic() { return eventTopic; }
    public void setEventTopic(String v) { this.eventTopic = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String v) { this.approvedBy = v; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(OffsetDateTime v) { this.approvedAt = v; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String v) { this.tenantId = v; }
    public String getPodId() { return podId; }
    public void setPodId(String v) { this.podId = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
