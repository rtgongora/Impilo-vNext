package zw.gov.mohcc.impilo.oros.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import zw.gov.mohcc.impilo.oros.domain.OrderType;

/**
 * Represents an orderable item in the facility catalog.
 */
@Entity
@Table(name = "oros_catalog_items",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "code"}))
public class CatalogItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "catalog_id", nullable = false)
    private UUID catalogId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    private OrderType orderType;

    @Column(name = "coding_system", nullable = false, length = 255)
    private String codingSystem = "http://impilo.gov.zw/coding";

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "display_name", nullable = false, length = 500)
    private String displayName;

    @Column(name = "coding_version", length = 50)
    private String codingVersion;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "zibo_canonical", length = 500)
    private String ziboCanonical;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public UUID getCatalogId() { return catalogId; }
    public void setCatalogId(UUID catalogId) { this.catalogId = catalogId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public OrderType getOrderType() { return orderType; }
    public void setOrderType(OrderType orderType) { this.orderType = orderType; }

    public String getCodingSystem() { return codingSystem; }
    public void setCodingSystem(String codingSystem) { this.codingSystem = codingSystem; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getCodingVersion() { return codingVersion; }
    public void setCodingVersion(String codingVersion) { this.codingVersion = codingVersion; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getZiboCanonical() { return ziboCanonical; }
    public void setZiboCanonical(String ziboCanonical) { this.ziboCanonical = ziboCanonical; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
