package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks facility-mode setup-wizard progress for a facility.
 *
 * <p>One row per facility (tenant-scoped). Persisted in TUSO (facility SoR) — never
 * in the stateless experience-bff. Drives the {@code FacilityModeContext.setupState}
 * read-model the shell consumes.</p>
 */
@Entity
@Table(name = "facility_setup_state", schema = "tuso")
public class FacilitySetupStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "departments_configured", nullable = false)
    private boolean departmentsConfigured;

    @Column(name = "service_points_configured", nullable = false)
    private boolean servicePointsConfigured;

    @Column(name = "queues_configured", nullable = false)
    private boolean queuesConfigured;

    @Column(name = "workflows_configured", nullable = false)
    private boolean workflowsConfigured;

    @Column(name = "workforce_linked", nullable = false)
    private boolean workforceLinked;

    @Column(name = "oros_routing_configured", nullable = false)
    private boolean orosRoutingConfigured;

    @Column(name = "khuluma_channels_configured", nullable = false)
    private boolean khulumaChannelsConfigured;

    @Column(name = "fundo_ready", nullable = false)
    private boolean fundoReady;

    @Column(name = "go_live", nullable = false)
    private boolean goLive;

    @Column(name = "go_live_at")
    private Instant goLiveAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getFacilityId() { return facilityId; }
    public void setFacilityId(Long facilityId) { this.facilityId = facilityId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public boolean isDepartmentsConfigured() { return departmentsConfigured; }
    public void setDepartmentsConfigured(boolean v) { this.departmentsConfigured = v; }
    public boolean isServicePointsConfigured() { return servicePointsConfigured; }
    public void setServicePointsConfigured(boolean v) { this.servicePointsConfigured = v; }
    public boolean isQueuesConfigured() { return queuesConfigured; }
    public void setQueuesConfigured(boolean v) { this.queuesConfigured = v; }
    public boolean isWorkflowsConfigured() { return workflowsConfigured; }
    public void setWorkflowsConfigured(boolean v) { this.workflowsConfigured = v; }
    public boolean isWorkforceLinked() { return workforceLinked; }
    public void setWorkforceLinked(boolean v) { this.workforceLinked = v; }
    public boolean isOrosRoutingConfigured() { return orosRoutingConfigured; }
    public void setOrosRoutingConfigured(boolean v) { this.orosRoutingConfigured = v; }
    public boolean isKhulumaChannelsConfigured() { return khulumaChannelsConfigured; }
    public void setKhulumaChannelsConfigured(boolean v) { this.khulumaChannelsConfigured = v; }
    public boolean isFundoReady() { return fundoReady; }
    public void setFundoReady(boolean v) { this.fundoReady = v; }
    public boolean isGoLive() { return goLive; }
    public void setGoLive(boolean v) { this.goLive = v; }
    public Instant getGoLiveAt() { return goLiveAt; }
    public void setGoLiveAt(Instant v) { this.goLiveAt = v; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
