package zw.gov.mohcc.impilo.governance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "wgv_jurisdiction_link")
public class JurisdictionLinkEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;

    @Column(name = "target_id", nullable = false, length = 128)
    private String targetId;

    @Column(name = "jurisdiction_id", nullable = false)
    private UUID jurisdictionId;

    @Column(name = "relationship_type", nullable = false, length = 64)
    private String relationshipType;

    @Column(name = "primary_flag", nullable = false)
    private boolean primaryFlag;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JurisdictionLinkEntity() {}

    public JurisdictionLinkEntity(UUID id, UUID tenantId, String targetType, String targetId, UUID jurisdictionId,
                                  String relationshipType, String status) {
        this.id = id;
        this.tenantId = tenantId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.jurisdictionId = jurisdictionId;
        this.relationshipType = relationshipType;
        this.status = status;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public UUID getJurisdictionId() { return jurisdictionId; }
    public String getRelationshipType() { return relationshipType; }
    public boolean isPrimaryFlag() { return primaryFlag; }
    public void setPrimaryFlag(boolean primaryFlag) { this.primaryFlag = primaryFlag; touch(); }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; touch(); }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; touch(); }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; touch(); }

    private void touch() { this.updatedAt = Instant.now(); }
}
