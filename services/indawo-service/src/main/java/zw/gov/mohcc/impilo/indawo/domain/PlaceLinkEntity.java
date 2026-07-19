package zw.gov.mohcc.impilo.indawo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Typed cross-registry place link (Place Journey Doctrine §6, D-L2).
 *
 * <p>The single home for Tuso↔Indawo relationships: Indawo is the writer, both
 * registries read. Refs are canonical UUIDs ({@code tuso.facility.facility_uuid}
 * / {@code ind_sites.site_id}); linked records keep separate authorities,
 * licences, inspections and lifecycles — a link never merges identity.</p>
 */
@Entity
@Table(name = "ind_place_links")
public class PlaceLinkEntity {

    public static final String REF_TUSO_FACILITY = "TUSO_FACILITY";
    public static final String REF_INDAWO_SITE = "INDAWO_SITE";

    public static final Set<String> LINK_TYPES = Set.of(
            "LOCATED_WITHIN", "SAME_CAMPUS_AS", "CONTAINS", "HOSTS_SERVICE_POINT",
            "TEMPORARY_SERVICE_AT", "REGULATED_COMPONENT_OF", "REPLACED_BY", "SERVES");

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ENDED = "ENDED";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "left_ref_type", nullable = false, length = 20)
    private String leftRefType;

    @Column(name = "left_ref_id", nullable = false)
    private UUID leftRefId;

    @Column(name = "right_ref_type", nullable = false, length = 20)
    private String rightRefType;

    @Column(name = "right_ref_id", nullable = false)
    private UUID rightRefId;

    @Column(name = "link_type", nullable = false, length = 32)
    private String linkType;

    @Column(name = "status", nullable = false, length = 16)
    private String status = STATUS_ACTIVE;

    @Column(name = "valid_from", nullable = false)
    private OffsetDateTime validFrom;

    @Column(name = "valid_to")
    private OffsetDateTime validTo;

    @Column(name = "provenance", length = 255)
    private String provenance;

    @Column(name = "steward_actor", length = 80)
    private String stewardActor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (validFrom == null) {
            validFrom = OffsetDateTime.now();
        }
        createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getLeftRefType() { return leftRefType; }
    public void setLeftRefType(String leftRefType) { this.leftRefType = leftRefType; }
    public UUID getLeftRefId() { return leftRefId; }
    public void setLeftRefId(UUID leftRefId) { this.leftRefId = leftRefId; }
    public String getRightRefType() { return rightRefType; }
    public void setRightRefType(String rightRefType) { this.rightRefType = rightRefType; }
    public UUID getRightRefId() { return rightRefId; }
    public void setRightRefId(UUID rightRefId) { this.rightRefId = rightRefId; }
    public String getLinkType() { return linkType; }
    public void setLinkType(String linkType) { this.linkType = linkType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(OffsetDateTime validFrom) { this.validFrom = validFrom; }
    public OffsetDateTime getValidTo() { return validTo; }
    public void setValidTo(OffsetDateTime validTo) { this.validTo = validTo; }
    public String getProvenance() { return provenance; }
    public void setProvenance(String provenance) { this.provenance = provenance; }
    public String getStewardActor() { return stewardActor; }
    public void setStewardActor(String stewardActor) { this.stewardActor = stewardActor; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
