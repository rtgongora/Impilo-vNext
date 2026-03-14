package zw.gov.mohcc.impilo.surv.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "alert_event", schema = "surv")
public class AlertEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "alert_definition_id", nullable = false)
    private Long alertDefinitionId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "syndrome_code", nullable = false, length = 128)
    private String syndromeCode;

    @Column(name = "trigger_count", nullable = false)
    private int triggerCount;

    @Column(name = "threshold", nullable = false)
    private int threshold;

    @Column(name = "triggered_at", nullable = false)
    private OffsetDateTime triggeredAt = OffsetDateTime.now();

    @Column(name = "acknowledged", nullable = false)
    private boolean acknowledged = false;

    public AlertEventEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public Long getAlertDefinitionId() { return alertDefinitionId; }
    public void setAlertDefinitionId(Long alertDefinitionId) { this.alertDefinitionId = alertDefinitionId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public String getSyndromeCode() { return syndromeCode; }
    public void setSyndromeCode(String syndromeCode) { this.syndromeCode = syndromeCode; }
    public int getTriggerCount() { return triggerCount; }
    public void setTriggerCount(int triggerCount) { this.triggerCount = triggerCount; }
    public int getThreshold() { return threshold; }
    public void setThreshold(int threshold) { this.threshold = threshold; }
    public OffsetDateTime getTriggeredAt() { return triggeredAt; }
    public void setTriggeredAt(OffsetDateTime triggeredAt) { this.triggeredAt = triggeredAt; }
    public boolean isAcknowledged() { return acknowledged; }
    public void setAcknowledged(boolean acknowledged) { this.acknowledged = acknowledged; }
}
