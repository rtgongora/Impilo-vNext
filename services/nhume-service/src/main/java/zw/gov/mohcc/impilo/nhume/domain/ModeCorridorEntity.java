package zw.gov.mohcc.impilo.nhume.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * OF-B20 §12.9 — one governed transport-mode corridor: a (geography, mode)
 * capability row with its eligibility constraints.
 *
 * <p><b>Honesty posture (CONFIG-ONLY — R67/OF-G21):</b> corridors are created
 * disabled and without evidence. Enabling one is a governance act that must
 * attach the §12.9 evidence pack ({@code evidenceRef}); the drone dispatch
 * gate additionally requires {@code weatherStatus == CLEAR} — an UNKNOWN
 * weather feed is a governed ROAD fallback, never a pass.</p>
 */
@Entity
@Table(name = "nhume_mode_corridors")
public class ModeCorridorEntity {

    @Id
    @Column(name = "corridor_id", nullable = false)
    private UUID corridorId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "corridor_code", nullable = false, length = 64)
    private String corridorCode;

    @Column(name = "display_name", length = 255)
    private String displayName;

    /** {@link DeliveryMode} name — DRONE for the autonomous lane. */
    @Column(name = "mode", nullable = false, length = 32)
    private String mode;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "max_payload_grams", precision = 10, scale = 0)
    private BigDecimal maxPayloadGrams;

    @Column(name = "max_distance_km", precision = 8, scale = 2)
    private BigDecimal maxDistanceKm;

    @Column(name = "cold_chain_allowed", nullable = false)
    private boolean coldChainAllowed;

    @Column(name = "controlled_allowed", nullable = false)
    private boolean controlledAllowed;

    /** CLEAR | BLOCKED | UNKNOWN — fail-closed: only CLEAR passes the gate. */
    @Column(name = "weather_status", nullable = false, length = 16)
    private String weatherStatus = "UNKNOWN";

    /** §12.9 evidence pack reference; an enabled corridor without one is a config lie. */
    @Column(name = "evidence_ref", length = 255)
    private String evidenceRef;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public ModeCorridorEntity() {}

    public UUID getCorridorId() { return corridorId; }
    public void setCorridorId(UUID v) { this.corridorId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getCorridorCode() { return corridorCode; }
    public void setCorridorCode(String v) { this.corridorCode = v; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String v) { this.displayName = v; }
    public String getMode() { return mode; }
    public void setMode(String v) { this.mode = v; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public BigDecimal getMaxPayloadGrams() { return maxPayloadGrams; }
    public void setMaxPayloadGrams(BigDecimal v) { this.maxPayloadGrams = v; }
    public BigDecimal getMaxDistanceKm() { return maxDistanceKm; }
    public void setMaxDistanceKm(BigDecimal v) { this.maxDistanceKm = v; }
    public boolean isColdChainAllowed() { return coldChainAllowed; }
    public void setColdChainAllowed(boolean v) { this.coldChainAllowed = v; }
    public boolean isControlledAllowed() { return controlledAllowed; }
    public void setControlledAllowed(boolean v) { this.controlledAllowed = v; }
    public String getWeatherStatus() { return weatherStatus; }
    public void setWeatherStatus(String v) { this.weatherStatus = v; }
    public String getEvidenceRef() { return evidenceRef; }
    public void setEvidenceRef(String v) { this.evidenceRef = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
