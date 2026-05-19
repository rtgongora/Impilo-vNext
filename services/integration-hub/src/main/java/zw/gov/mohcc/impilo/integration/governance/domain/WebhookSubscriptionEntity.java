package zw.gov.mohcc.impilo.integration.governance.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ih_webhook_subscriptions")
public class WebhookSubscriptionEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "external_app_id", length = 36, nullable = false)
    private String externalAppId;

    @Column(name = "integration_contract_id", length = 36, nullable = false)
    private String integrationContractId;

    @Lob
    @Column(name = "event_topics", columnDefinition = "TEXT", nullable = false)
    private String eventTopicsCsv;

    @Column(name = "delivery_url", length = 1024, nullable = false)
    private String deliveryUrl;

    @Column(name = "signature_secret_ref", length = 256, nullable = false)
    private String signatureSecretRef;

    @Column(name = "signature_method", length = 32, nullable = false)
    private String signatureMethod;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "filter_facility_ids", columnDefinition = "TEXT")
    private String filterFacilityIdsCsv;

    @Column(name = "filter_programme_ids", columnDefinition = "TEXT")
    private String filterProgrammeIdsCsv;

    @Lob
    @Column(name = "filter_extra_json", columnDefinition = "TEXT")
    private String filterExtraJson;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries = 5;

    @Column(name = "initial_delay_ms", nullable = false)
    private int initialDelayMs = 1000;

    @Column(name = "max_delay_ms", nullable = false)
    private int maxDelayMs = 60000;

    @Column(name = "dead_letter_after_retries", nullable = false)
    private int deadLetterAfterRetries = 10;

    @Column(name = "last_delivery_at")
    private OffsetDateTime lastDeliveryAt;

    @Column(name = "last_delivery_status", length = 16)
    private String lastDeliveryStatus;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures = 0;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "pod_id", length = 64, nullable = false)
    private String podId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getExternalAppId() { return externalAppId; }
    public void setExternalAppId(String v) { this.externalAppId = v; }
    public String getIntegrationContractId() { return integrationContractId; }
    public void setIntegrationContractId(String v) { this.integrationContractId = v; }
    public String getEventTopicsCsv() { return eventTopicsCsv; }
    public void setEventTopicsCsv(String v) { this.eventTopicsCsv = v; }
    public String getDeliveryUrl() { return deliveryUrl; }
    public void setDeliveryUrl(String v) { this.deliveryUrl = v; }
    public String getSignatureSecretRef() { return signatureSecretRef; }
    public void setSignatureSecretRef(String v) { this.signatureSecretRef = v; }
    public String getSignatureMethod() { return signatureMethod; }
    public void setSignatureMethod(String v) { this.signatureMethod = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
    public String getFilterFacilityIdsCsv() { return filterFacilityIdsCsv; }
    public void setFilterFacilityIdsCsv(String v) { this.filterFacilityIdsCsv = v; }
    public String getFilterProgrammeIdsCsv() { return filterProgrammeIdsCsv; }
    public void setFilterProgrammeIdsCsv(String v) { this.filterProgrammeIdsCsv = v; }
    public String getFilterExtraJson() { return filterExtraJson; }
    public void setFilterExtraJson(String v) { this.filterExtraJson = v; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int v) { this.maxRetries = v; }
    public int getInitialDelayMs() { return initialDelayMs; }
    public void setInitialDelayMs(int v) { this.initialDelayMs = v; }
    public int getMaxDelayMs() { return maxDelayMs; }
    public void setMaxDelayMs(int v) { this.maxDelayMs = v; }
    public int getDeadLetterAfterRetries() { return deadLetterAfterRetries; }
    public void setDeadLetterAfterRetries(int v) { this.deadLetterAfterRetries = v; }
    public OffsetDateTime getLastDeliveryAt() { return lastDeliveryAt; }
    public void setLastDeliveryAt(OffsetDateTime v) { this.lastDeliveryAt = v; }
    public String getLastDeliveryStatus() { return lastDeliveryStatus; }
    public void setLastDeliveryStatus(String v) { this.lastDeliveryStatus = v; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(int v) { this.consecutiveFailures = v; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String v) { this.tenantId = v; }
    public String getPodId() { return podId; }
    public void setPodId(String v) { this.podId = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
