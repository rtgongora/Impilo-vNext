package zw.gov.mohcc.impilo.daidzai.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dai_resource_request", schema = "daidzai")
public class ResourceRequestEntity {

    @Id @Column(name = "id") private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "incident_id", nullable = false) private UUID incidentId;
    @Column(name = "resource_type", nullable = false, length = 32) private String resourceType;
    @Column(name = "resource_owner", nullable = false, length = 32) private String resourceOwner;
    @Column(name = "quantity", nullable = false) private Integer quantity = 1;
    @Column(name = "detail", length = 512) private String detail;
    @Column(name = "owner_request_ref", length = 128) private String ownerRequestRef;
    @Column(name = "status", nullable = false, length = 24) private String status = "REQUESTED";
    @Column(name = "requested_by", length = 128) private String requestedBy;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    @PrePersist void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getIncidentId() { return incidentId; }
    public void setIncidentId(UUID v) { this.incidentId = v; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String v) { this.resourceType = v; }
    public String getResourceOwner() { return resourceOwner; }
    public void setResourceOwner(String v) { this.resourceOwner = v; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer v) { this.quantity = v; }
    public String getDetail() { return detail; }
    public void setDetail(String v) { this.detail = v; }
    public String getOwnerRequestRef() { return ownerRequestRef; }
    public void setOwnerRequestRef(String v) { this.ownerRequestRef = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String v) { this.requestedBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
