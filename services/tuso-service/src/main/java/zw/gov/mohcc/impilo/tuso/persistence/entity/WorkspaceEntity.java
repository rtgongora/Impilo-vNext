package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "workspace", schema = "tuso")
public class WorkspaceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private FacilityEntity facility;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "workspace_type", nullable = false, length = 50)
    private String workspaceType;

    @Column(name = "zibo_validated", nullable = false)
    private Boolean ziboValidated = false;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "queue_config", columnDefinition = "jsonb")
    private Map<String, Object> queueConfig;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_panels", columnDefinition = "jsonb")
    private Map<String, Object> defaultPanels;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and setters

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public FacilityEntity getFacility() { return facility; }
    public void setFacility(FacilityEntity facility) { this.facility = facility; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getWorkspaceType() { return workspaceType; }
    public void setWorkspaceType(String workspaceType) { this.workspaceType = workspaceType; }

    public Boolean getZiboValidated() { return ziboValidated; }
    public void setZiboValidated(Boolean ziboValidated) { this.ziboValidated = ziboValidated; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, Object> getQueueConfig() { return queueConfig; }
    public void setQueueConfig(Map<String, Object> queueConfig) { this.queueConfig = queueConfig; }

    public Map<String, Object> getDefaultPanels() { return defaultPanels; }
    public void setDefaultPanels(Map<String, Object> defaultPanels) { this.defaultPanels = defaultPanels; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
