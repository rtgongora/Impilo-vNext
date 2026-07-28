package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One invocation of a CKP-defined order-set bundle (step_type=ORDER_SET) for a specific emergency
 * episode (pct V206, W7b). CKP holds what a bundle IS; this tracks what actually happened.
 */
@Entity
@Table(name = "emergency_order_set_instance")
public class EmergencyOrderSetInstanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "instance_id")
    private UUID instanceId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "visit_id")
    private UUID visitId;

    @Column(name = "pathway_session_id")
    private UUID pathwaySessionId;

    /** Content-reference string, not a foreign key — CKP is a different service/database. */
    @Column(name = "order_set_code", nullable = false)
    private String orderSetCode;

    @Column(name = "order_set_name")
    private String orderSetName;

    @Column(name = "status", nullable = false)
    private String status = "IN_PROGRESS";

    @Column(name = "invoked_by", nullable = false)
    private String invokedBy;

    @Column(name = "invoked_at", nullable = false)
    private OffsetDateTime invokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (invokedAt == null) invokedAt = now;
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getInstanceId() { return instanceId; }
    public void setInstanceId(UUID instanceId) { this.instanceId = instanceId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID episodeId) { this.episodeId = episodeId; }
    public UUID getVisitId() { return visitId; }
    public void setVisitId(UUID visitId) { this.visitId = visitId; }
    public UUID getPathwaySessionId() { return pathwaySessionId; }
    public void setPathwaySessionId(UUID pathwaySessionId) { this.pathwaySessionId = pathwaySessionId; }
    public String getOrderSetCode() { return orderSetCode; }
    public void setOrderSetCode(String orderSetCode) { this.orderSetCode = orderSetCode; }
    public String getOrderSetName() { return orderSetName; }
    public void setOrderSetName(String orderSetName) { this.orderSetName = orderSetName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getInvokedBy() { return invokedBy; }
    public void setInvokedBy(String invokedBy) { this.invokedBy = invokedBy; }
    public OffsetDateTime getInvokedAt() { return invokedAt; }
    public void setInvokedAt(OffsetDateTime invokedAt) { this.invokedAt = invokedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
