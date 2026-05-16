package zw.gov.mohcc.impilo.msikaapps.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ma_audit_events")
public class AuditEventEntity {
    @Id
    private String id = UUID.randomUUID().toString();
    @Column(name = "item_id")               private String itemId;
    @Column(name = "installation_id")       private String installationId;
    @Column(name = "activation_request_id") private String activationRequestId;
    @Column(name = "external_app_id")       private String externalAppId;
    @Column(name = "event_type", nullable = false) private String eventType;
    @Column(name = "actor_id",   nullable = false) private String actorId;
    @Column(name = "actor_type") private String actorType;
    @Column(name = "tenant_id", nullable = false) private String tenantId;
    @Column(name = "facility_id") private String facilityId;
    @Column(columnDefinition = "TEXT") private String reason;
    @Column(name = "correlation_id") private String correlationId;
    @Column(name = "occurred_at", nullable = false) private OffsetDateTime occurredAt = OffsetDateTime.now();

    public String getId() { return id; } public void setId(String s) { this.id = s; }
    public String getItemId() { return itemId; } public void setItemId(String s) { this.itemId = s; }
    public String getInstallationId() { return installationId; } public void setInstallationId(String s) { this.installationId = s; }
    public String getActivationRequestId() { return activationRequestId; } public void setActivationRequestId(String s) { this.activationRequestId = s; }
    public String getExternalAppId() { return externalAppId; } public void setExternalAppId(String s) { this.externalAppId = s; }
    public String getEventType() { return eventType; } public void setEventType(String s) { this.eventType = s; }
    public String getActorId() { return actorId; } public void setActorId(String s) { this.actorId = s; }
    public String getActorType() { return actorType; } public void setActorType(String s) { this.actorType = s; }
    public String getTenantId() { return tenantId; } public void setTenantId(String s) { this.tenantId = s; }
    public String getFacilityId() { return facilityId; } public void setFacilityId(String s) { this.facilityId = s; }
    public String getReason() { return reason; } public void setReason(String s) { this.reason = s; }
    public String getCorrelationId() { return correlationId; } public void setCorrelationId(String s) { this.correlationId = s; }
    public OffsetDateTime getOccurredAt() { return occurredAt; } public void setOccurredAt(OffsetDateTime t) { this.occurredAt = t; }
}
