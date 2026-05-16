package zw.gov.mohcc.impilo.nhume.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "nhume_delivery_webhook_events")
public class DeliveryWebhookEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_code", length = 64)
    private String providerCode;

    @Column(name = "delivery_id")
    private UUID deliveryId;

    @Column(name = "mission_id")
    private UUID missionId;

    @Column(name = "event_kind", length = 64)
    private String eventKind;

    @Column(name = "signature_valid", nullable = false)
    private boolean signatureValid;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt = OffsetDateTime.now();

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "processed", nullable = false)
    private boolean processed;

    @Column(name = "processing_error", columnDefinition = "TEXT")
    private String processingError;

    public DeliveryWebhookEventEntity() {}

    public Long getId() { return id; }
    public String getProviderCode() { return providerCode; }
    public void setProviderCode(String v) { this.providerCode = v; }
    public UUID getDeliveryId() { return deliveryId; }
    public void setDeliveryId(UUID v) { this.deliveryId = v; }
    public UUID getMissionId() { return missionId; }
    public void setMissionId(UUID v) { this.missionId = v; }
    public String getEventKind() { return eventKind; }
    public void setEventKind(String v) { this.eventKind = v; }
    public boolean isSignatureValid() { return signatureValid; }
    public void setSignatureValid(boolean v) { this.signatureValid = v; }
    public OffsetDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(OffsetDateTime v) { this.receivedAt = v; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String v) { this.payloadJson = v; }
    public boolean isProcessed() { return processed; }
    public void setProcessed(boolean v) { this.processed = v; }
    public String getProcessingError() { return processingError; }
    public void setProcessingError(String v) { this.processingError = v; }
}
