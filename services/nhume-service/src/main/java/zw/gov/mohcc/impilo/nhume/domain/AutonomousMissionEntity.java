package zw.gov.mohcc.impilo.nhume.domain;

import jakarta.persistence.Column;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "nhume_autonomous_missions")
public class AutonomousMissionEntity {

    @Id
    @Column(name = "mission_id", nullable = false)
    private UUID missionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "delivery_id")
    private UUID deliveryId;

    @Column(name = "asset_id")
    private UUID assetId;

    @Column(name = "provider_code", length = 64)
    private String providerCode;

    @Column(name = "mission_kind", nullable = false, length = 48)
    private String missionKind = "DRONE";

    @Column(name = "status", nullable = false, length = 32)
    private String status = "DRAFT";

    /** OF-B20 §12.9 — the governed corridor this mission was planned against. */
    @Column(name = "corridor_code", length = 64)
    private String corridorCode;

    /** OF-B20 journey #67 — set when the weather/eligibility gate forced a ROAD fallback. */
    @Column(name = "fallback_reason", length = 255)
    private String fallbackReason;

    /**
     * OF-B20 HONESTY LAW (CONFIG-ONLY — R67/OF-G21): actual flight operations are
     * outside the estate. No code path may record a mission as having flown —
     * any attempt to persist a flight-claim status is rejected at the domain
     * boundary. Missions exist so the capability is governed and auditable and
     * the ROAD fallback is real; the first honest flight claim requires the
     * §12.9 evidence pack and a deliberate removal of this guard.
     */
    private static final java.util.Set<String> FLIGHT_CLAIM_STATUSES = java.util.Set.of(
            "FLOWN", "IN_FLIGHT", "AIRBORNE", "LANDED", "FLIGHT_COMPLETED", "FLIGHT_IN_PROGRESS");

    @Column(name = "launch_site", length = 255)
    private String launchSite;

    @Column(name = "landing_site", length = 255)
    private String landingSite;

    @Column(name = "scheduled_for")
    private OffsetDateTime scheduledFor;

    @Column(name = "launched_at")
    private OffsetDateTime launchedAt;

    @Column(name = "arrived_at")
    private OffsetDateTime arrivedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "simulation_mode", nullable = false)
    private boolean simulationMode = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "telemetry_json", nullable = false, columnDefinition = "jsonb")
    private String telemetryJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_json", columnDefinition = "jsonb")
    private String rawJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public AutonomousMissionEntity() {}

    public UUID getMissionId() { return missionId; }
    public void setMissionId(UUID v) { this.missionId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getDeliveryId() { return deliveryId; }
    public void setDeliveryId(UUID v) { this.deliveryId = v; }
    public UUID getAssetId() { return assetId; }
    public void setAssetId(UUID v) { this.assetId = v; }
    public String getProviderCode() { return providerCode; }
    public void setProviderCode(String v) { this.providerCode = v; }
    public String getMissionKind() { return missionKind; }
    public void setMissionKind(String v) { this.missionKind = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) {
        if (v != null && FLIGHT_CLAIM_STATUSES.contains(v.toUpperCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("MISSION_FLIGHT_CLAIM_FORBIDDEN: status '" + v
                    + "' would claim an actual flight. Drone delivery is a governed CONFIG-ONLY"
                    + " capability (OF-B20/OF-G21/R67) — no code path may record a flown mission.");
        }
        this.status = v;
    }
    public String getCorridorCode() { return corridorCode; }
    public void setCorridorCode(String v) { this.corridorCode = v; }
    public String getFallbackReason() { return fallbackReason; }
    public void setFallbackReason(String v) { this.fallbackReason = v; }
    public String getLaunchSite() { return launchSite; }
    public void setLaunchSite(String v) { this.launchSite = v; }
    public String getLandingSite() { return landingSite; }
    public void setLandingSite(String v) { this.landingSite = v; }
    public OffsetDateTime getScheduledFor() { return scheduledFor; }
    public void setScheduledFor(OffsetDateTime v) { this.scheduledFor = v; }
    public OffsetDateTime getLaunchedAt() { return launchedAt; }
    public void setLaunchedAt(OffsetDateTime v) { this.launchedAt = v; }
    public OffsetDateTime getArrivedAt() { return arrivedAt; }
    public void setArrivedAt(OffsetDateTime v) { this.arrivedAt = v; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime v) { this.completedAt = v; }
    public boolean isSimulationMode() { return simulationMode; }
    public void setSimulationMode(boolean v) { this.simulationMode = v; }
    public String getTelemetryJson() { return telemetryJson; }
    public void setTelemetryJson(String v) { this.telemetryJson = v; }
    public String getRawJson() { return rawJson; }
    public void setRawJson(String v) { this.rawJson = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
