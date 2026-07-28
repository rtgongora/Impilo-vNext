package zw.gov.mohcc.impilo.surgery.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A drain, stoma or wound as a longitudinal object (SB-2, surgical domain pack §17). Implants
 * stay federated to inventory-service's own registry (P8) — see V006's own header.
 */
@Entity
@Table(name = "surgical_longitudinal_object", schema = "surgery")
public class SurgicalLongitudinalObjectEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "surgical_episode_id", nullable = false)
    private UUID surgicalEpisodeId;

    @Column(nullable = false, length = 16)
    private String kind;

    @Column(nullable = false, length = 128)
    private String site;

    @Column(name = "placed_at", nullable = false)
    private OffsetDateTime placedAt;

    @Column(nullable = false)
    private String operator;

    @Column(nullable = false, columnDefinition = "text")
    private String indication;

    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "removed_at")
    private OffsetDateTime removedAt;

    @Column(name = "removed_by")
    private String removedBy;

    @Column(name = "removal_reason", columnDefinition = "text")
    private String removalReason;

    @Column(name = "revised_to_object_id")
    private UUID revisedToObjectId;

    @Column(name = "implant_registry_ref")
    private UUID implantRegistryRef;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (placedAt == null) placedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getSurgicalEpisodeId() { return surgicalEpisodeId; }
    public void setSurgicalEpisodeId(UUID v) { this.surgicalEpisodeId = v; }
    public String getKind() { return kind; }
    public void setKind(String v) { this.kind = v; }
    public String getSite() { return site; }
    public void setSite(String v) { this.site = v; }
    public OffsetDateTime getPlacedAt() { return placedAt; }
    public void setPlacedAt(OffsetDateTime v) { this.placedAt = v; }
    public String getOperator() { return operator; }
    public void setOperator(String v) { this.operator = v; }
    public String getIndication() { return indication; }
    public void setIndication(String v) { this.indication = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public OffsetDateTime getRemovedAt() { return removedAt; }
    public void setRemovedAt(OffsetDateTime v) { this.removedAt = v; }
    public String getRemovedBy() { return removedBy; }
    public void setRemovedBy(String v) { this.removedBy = v; }
    public String getRemovalReason() { return removalReason; }
    public void setRemovalReason(String v) { this.removalReason = v; }
    public UUID getRevisedToObjectId() { return revisedToObjectId; }
    public void setRevisedToObjectId(UUID v) { this.revisedToObjectId = v; }
    public UUID getImplantRegistryRef() { return implantRegistryRef; }
    public void setImplantRegistryRef(UUID v) { this.implantRegistryRef = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
