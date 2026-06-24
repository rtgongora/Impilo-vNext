package zw.gov.mohcc.impilo.datagovernance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Per-user privacy display preference — PII masking level, inactivity auto-lock,
 * and screen-share protection. Owned by the Privacy-by-Architecture plane (§6).
 */
@Entity
@Table(name = "dgv_privacy_display_preference")
public class PrivacyDisplayPreferenceEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Column(name = "default_level", nullable = false, length = 10)
    private String defaultLevel = "PARTIAL";

    @Column(name = "auto_lock_minutes", nullable = false)
    private int autoLockMinutes = 5;

    @Column(name = "screen_share_mode", nullable = false)
    private boolean screenShareMode = true;

    // Display settings (G048) — the same per-user display-preference row also carries UI ergonomics.
    @Column(name = "theme", nullable = false, length = 10)
    private String theme = "system";

    @Column(name = "font_size", nullable = false, length = 10)
    private String fontSize = "medium";

    @Column(name = "density", nullable = false, length = 16)
    private String density = "comfortable";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected PrivacyDisplayPreferenceEntity() {}

    public PrivacyDisplayPreferenceEntity(UUID tenantId, String userId, String defaultLevel,
                                          int autoLockMinutes, boolean screenShareMode) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.defaultLevel = defaultLevel;
        this.autoLockMinutes = autoLockMinutes;
        this.screenShareMode = screenShareMode;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public String getDefaultLevel() { return defaultLevel; }
    public int getAutoLockMinutes() { return autoLockMinutes; }
    public boolean isScreenShareMode() { return screenShareMode; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public String getTheme() { return theme; }
    public String getFontSize() { return fontSize; }
    public String getDensity() { return density; }

    public void setDefaultLevel(String defaultLevel) { this.defaultLevel = defaultLevel; }
    public void setAutoLockMinutes(int autoLockMinutes) { this.autoLockMinutes = autoLockMinutes; }
    public void setScreenShareMode(boolean screenShareMode) { this.screenShareMode = screenShareMode; }
    public void setTheme(String theme) { this.theme = theme; }
    public void setFontSize(String fontSize) { this.fontSize = fontSize; }
    public void setDensity(String density) { this.density = density; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
