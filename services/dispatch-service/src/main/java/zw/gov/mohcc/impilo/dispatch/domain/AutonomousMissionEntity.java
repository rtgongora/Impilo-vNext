package zw.gov.mohcc.impilo.dispatch.domain;

import jakarta.persistence.Column;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dsp_autonomous_missions")
public class AutonomousMissionEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "delivery_id")
    private UUID deliveryId;

    @Column(name = "mission_code", nullable = false)
    private String missionCode;

    @Column(name = "vehicle_ref")
    private String vehicleRef;

    @Column(name = "status", nullable = false)
    private String status = "PLANNED";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mission_payload_json", columnDefinition = "jsonb")
    private String missionPayloadJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected AutonomousMissionEntity() {}

    public AutonomousMissionEntity(UUID id, UUID tenantId, String missionCode) {
        this.id = id;
        this.tenantId = tenantId;
        this.missionCode = missionCode;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getDeliveryId() { return deliveryId; }
    public void setDeliveryId(UUID deliveryId) { this.deliveryId = deliveryId; }
    public String getMissionCode() { return missionCode; }
    public String getVehicleRef() { return vehicleRef; }
    public void setVehicleRef(String vehicleRef) { this.vehicleRef = vehicleRef; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMissionPayloadJson() { return missionPayloadJson; }
    public void setMissionPayloadJson(String missionPayloadJson) { this.missionPayloadJson = missionPayloadJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
