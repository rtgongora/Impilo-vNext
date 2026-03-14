package zw.gov.mohcc.impilo.offlineedge.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ofe_captured_actions")
public class CapturedActionEntity {
    @Id @Column(name = "action_id") private UUID actionId;
    @Column(name = "entitlement_id", nullable = false) private UUID entitlementId;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "actor_id", nullable = false) private String actorId;
    @Column(name = "patient_ref", nullable = false) private String patientRef;
    @Column(name = "workflow_type", nullable = false) private String workflowType = "CAPTURE_VITALS";
    @Column(name = "action_type", nullable = false) private String actionType;
    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT") private String payloadJson;
    @Column(name = "captured_at", nullable = false) private OffsetDateTime capturedAt;
    @Column(name = "device_id") private String deviceId;
    @Column(name = "sequence_num", nullable = false) private int sequenceNum = 1;
    @Column(nullable = false) private String status = "QUEUED";
    @Column(name = "replayed_at") private OffsetDateTime replayedAt;
    @Column(name = "replay_error", columnDefinition = "TEXT") private String replayError;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();

    protected CapturedActionEntity() {}
    public CapturedActionEntity(UUID actionId, UUID entitlementId, UUID tenantId, String actorId,
                                 String patientRef, String actionType, String payloadJson,
                                 OffsetDateTime capturedAt, String deviceId, int sequenceNum) {
        this.actionId = actionId; this.entitlementId = entitlementId; this.tenantId = tenantId;
        this.actorId = actorId; this.patientRef = patientRef; this.actionType = actionType;
        this.payloadJson = payloadJson; this.capturedAt = capturedAt; this.deviceId = deviceId;
        this.sequenceNum = sequenceNum;
    }

    public UUID getActionId() { return actionId; }
    public UUID getEntitlementId() { return entitlementId; }
    public UUID getTenantId() { return tenantId; }
    public String getActorId() { return actorId; }
    public String getPatientRef() { return patientRef; }
    public String getWorkflowType() { return workflowType; }
    public String getActionType() { return actionType; }
    public String getPayloadJson() { return payloadJson; }
    public OffsetDateTime getCapturedAt() { return capturedAt; }
    public String getDeviceId() { return deviceId; }
    public int getSequenceNum() { return sequenceNum; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public OffsetDateTime getReplayedAt() { return replayedAt; }
    public void setReplayedAt(OffsetDateTime v) { this.replayedAt = v; }
    public String getReplayError() { return replayError; }
    public void setReplayError(String v) { this.replayError = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
