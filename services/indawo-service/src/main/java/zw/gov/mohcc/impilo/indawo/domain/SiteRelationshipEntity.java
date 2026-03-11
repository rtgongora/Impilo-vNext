package zw.gov.mohcc.impilo.indawo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ind_site_relationships")
public class SiteRelationshipEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "parent_site_id", nullable = false)
    private UUID parentSiteId;

    @Column(name = "child_site_id", nullable = false)
    private UUID childSiteId;

    @Column(name = "rel_type", nullable = false, length = 64)
    private String relType;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected SiteRelationshipEntity() {}

    public SiteRelationshipEntity(UUID parentSiteId, UUID childSiteId, String relType) {
        this.id = UUID.randomUUID();
        this.parentSiteId = parentSiteId;
        this.childSiteId = childSiteId;
        this.relType = relType;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getParentSiteId() { return parentSiteId; }
    public UUID getChildSiteId() { return childSiteId; }
    public String getRelType() { return relType; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
