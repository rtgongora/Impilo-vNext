package zw.gov.mohcc.impilo.experience.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "workspaces")
public class Workspace {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(nullable = false)
    private String name;

    @Column(name = "workspace_type", nullable = false)
    private String workspaceType;

    @Column(nullable = false)
    private String status;

    @Column(columnDefinition = "jsonb")
    private String config;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @Column(name = "activated_by")
    private String activatedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Workspace() {}

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getFacilityId() { return facilityId; }
    public String getName() { return name; }
    public String getWorkspaceType() { return workspaceType; }
    public String getStatus() { return status; }
    public String getConfig() { return config; }
    public OffsetDateTime getActivatedAt() { return activatedAt; }
    public String getActivatedBy() { return activatedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void activate(String actorId) {
        this.status = "ACTIVE";
        this.activatedAt = OffsetDateTime.now();
        this.activatedBy = actorId;
        this.updatedAt = OffsetDateTime.now();
    }
}
