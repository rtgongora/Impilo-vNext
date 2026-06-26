package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A training venue / site (V016): physical room, virtual room, or hybrid.
 * {@code facility_level} aligns to the pathway facility-level vocabulary;
 * {@code location_ref} absorbs legacy free-text session locations on migration.
 * Facility identity remains owned by TUSO — this is a learning-delivery venue,
 * not a facility master record.
 */
@Entity
@Table(name = "lrn_training_venue")
public class TrainingVenueEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "venue_kind", nullable = false, length = 32)
    private String venueKind = "PHYSICAL";

    @Column(name = "facility_level", length = 64)
    private String facilityLevel;

    @Column(name = "location_ref", length = 1024)
    private String locationRef;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getVenueKind() { return venueKind; }
    public void setVenueKind(String venueKind) { this.venueKind = venueKind; }
    public String getFacilityLevel() { return facilityLevel; }
    public void setFacilityLevel(String facilityLevel) { this.facilityLevel = facilityLevel; }
    public String getLocationRef() { return locationRef; }
    public void setLocationRef(String locationRef) { this.locationRef = locationRef; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
