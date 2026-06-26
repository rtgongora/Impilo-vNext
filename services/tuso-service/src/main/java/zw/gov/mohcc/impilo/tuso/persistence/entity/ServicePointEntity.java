package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A service point: an operational sub-unit of a facility (e.g. a consultation room,
 * triage desk, dispensing window) optionally bound to a facility unit (department)
 * and a queue. Configured by the facility-mode setup wizard. TUSO is the SoR.
 */
@Entity
@Table(name = "service_point", schema = "tuso")
public class ServicePointEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Column(name = "facility_unit_id")
    private Long facilityUnitId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "code", length = 64)
    private String code;

    @Column(name = "service_point_type", nullable = false, length = 64)
    private String servicePointType = "CONSULTATION";

    @Column(name = "queue_id", length = 128)
    private String queueId;

    @Column(name = "workflow_archetype", length = 64)
    private String workflowArchetype;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "active", nullable = false)
    private boolean active = true;

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

    public UUID getId() { return id; }
    public Long getFacilityId() { return facilityId; }
    public void setFacilityId(Long facilityId) { this.facilityId = facilityId; }
    public Long getFacilityUnitId() { return facilityUnitId; }
    public void setFacilityUnitId(Long facilityUnitId) { this.facilityUnitId = facilityUnitId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getServicePointType() { return servicePointType; }
    public void setServicePointType(String servicePointType) { this.servicePointType = servicePointType; }
    public String getQueueId() { return queueId; }
    public void setQueueId(String queueId) { this.queueId = queueId; }
    public String getWorkflowArchetype() { return workflowArchetype; }
    public void setWorkflowArchetype(String workflowArchetype) { this.workflowArchetype = workflowArchetype; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
