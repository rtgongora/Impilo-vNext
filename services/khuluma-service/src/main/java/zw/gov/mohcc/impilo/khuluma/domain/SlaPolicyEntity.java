package zw.gov.mohcc.impilo.khuluma.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A per-tenant, per-priority SLA override for khuluma escalations (W4, G-KH-01). */
@Entity
@Table(name = "khuluma_sla_policy")
public class SlaPolicyEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(nullable = false, length = 16) private String priority;
    @Column(name = "first_response_minutes", nullable = false) private int firstResponseMinutes;
    @Column(name = "resolution_minutes", nullable = false) private int resolutionMinutes;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;

    @PrePersist void onCreate() { if (createdAt == null) createdAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getPriority() { return priority; }
    public void setPriority(String v) { this.priority = v; }
    public int getFirstResponseMinutes() { return firstResponseMinutes; }
    public void setFirstResponseMinutes(int v) { this.firstResponseMinutes = v; }
    public int getResolutionMinutes() { return resolutionMinutes; }
    public void setResolutionMinutes(int v) { this.resolutionMinutes = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
}
