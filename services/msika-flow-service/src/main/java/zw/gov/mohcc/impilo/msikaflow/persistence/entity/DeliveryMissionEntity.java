package zw.gov.mohcc.impilo.msikaflow.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.msikaflow.domain.MissionStatus;
import zw.gov.mohcc.impilo.msikaflow.domain.MissionType;

import java.time.OffsetDateTime;

@Entity
@Table(name = "mf_delivery_missions")
public class DeliveryMissionEntity {

    @Id
    @Column(name = "mission_id", nullable = false, length = 26)
    private String missionId;

    @Column(name = "leg_id", nullable = false, length = 26)
    private String legId;

    @Enumerated(EnumType.STRING)
    @Column(name = "mission_type", nullable = false, length = 40)
    private MissionType missionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "mission_status", nullable = false, length = 30)
    private MissionStatus missionStatus = MissionStatus.PLANNED;

    @Column(name = "command_ref", length = 255)
    private String commandRef;

    @Column(name = "telemetry_ref", length = 255)
    private String telemetryRef;

    @Column(name = "launch_at")
    private OffsetDateTime launchAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadataJson = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        if (metadataJson == null) metadataJson = "{}";
    }

    public String getMissionId() { return missionId; }
    public void setMissionId(String missionId) { this.missionId = missionId; }
    public String getLegId() { return legId; }
    public void setLegId(String legId) { this.legId = legId; }
    public MissionType getMissionType() { return missionType; }
    public void setMissionType(MissionType missionType) { this.missionType = missionType; }
    public MissionStatus getMissionStatus() { return missionStatus; }
    public void setMissionStatus(MissionStatus missionStatus) { this.missionStatus = missionStatus; }
    public String getCommandRef() { return commandRef; }
    public void setCommandRef(String commandRef) { this.commandRef = commandRef; }
    public String getTelemetryRef() { return telemetryRef; }
    public void setTelemetryRef(String telemetryRef) { this.telemetryRef = telemetryRef; }
    public OffsetDateTime getLaunchAt() { return launchAt; }
    public void setLaunchAt(OffsetDateTime launchAt) { this.launchAt = launchAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}

