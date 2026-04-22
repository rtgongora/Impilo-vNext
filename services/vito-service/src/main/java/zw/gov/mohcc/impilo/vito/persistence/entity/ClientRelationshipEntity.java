package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import zw.gov.mohcc.impilo.vito.core.ClientRelationshipType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "client_relationship", schema = "vito")
public class ClientRelationshipEntity {

    @Id
    @Column(name = "relationship_id", nullable = false, updatable = false)
    private UUID relationshipId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_health_id", nullable = false)
    private UUID clientHealthId;

    @Column(name = "related_client_health_id", nullable = false)
    private UUID relatedClientHealthId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false)
    private ClientRelationshipType relationshipType;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (relationshipId == null) {
            relationshipId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public UUID getRelationshipId() { return relationshipId; }
    public void setRelationshipId(UUID relationshipId) { this.relationshipId = relationshipId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getClientHealthId() { return clientHealthId; }
    public void setClientHealthId(UUID clientHealthId) { this.clientHealthId = clientHealthId; }
    public UUID getRelatedClientHealthId() { return relatedClientHealthId; }
    public void setRelatedClientHealthId(UUID relatedClientHealthId) { this.relatedClientHealthId = relatedClientHealthId; }
    public ClientRelationshipType getRelationshipType() { return relationshipType; }
    public void setRelationshipType(ClientRelationshipType relationshipType) { this.relationshipType = relationshipType; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
