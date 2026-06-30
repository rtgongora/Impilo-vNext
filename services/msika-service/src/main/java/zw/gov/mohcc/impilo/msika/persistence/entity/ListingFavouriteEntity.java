package zw.gov.mohcc.impilo.msika.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Buyer favourite — display preference only. */
@Entity
@Table(name = "msika_listing_favourites")
public class ListingFavouriteEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "actor_id", nullable = false, length = 128)
    private String actorId;

    @Column(name = "listing_id", nullable = false, length = 26)
    private String listingId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getListingId() { return listingId; }
    public void setListingId(String listingId) { this.listingId = listingId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
