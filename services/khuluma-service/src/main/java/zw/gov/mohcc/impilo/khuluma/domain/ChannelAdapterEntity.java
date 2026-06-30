package zw.gov.mohcc.impilo.khuluma.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A per-tenant external-channel adapter and its configuration status (W6, G-KH-03). */
@Entity
@Table(name = "khuluma_channel_adapter")
public class ChannelAdapterEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(nullable = false, length = 16) private String channel;
    @Column(nullable = false, length = 24) private String status = "NOT_CONFIGURED";
    @Column(length = 64) private String provider;
    @Column(name = "config_json", columnDefinition = "jsonb") private String configJson;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    @PrePersist void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getChannel() { return channel; }
    public void setChannel(String v) { this.channel = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getProvider() { return provider; }
    public void setProvider(String v) { this.provider = v; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String v) { this.configJson = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
