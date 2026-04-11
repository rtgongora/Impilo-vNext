package zw.gov.mohcc.impilo.oros.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents an individual item within a clinical order.
 * Each order can contain multiple items (e.g., multiple lab tests).
 */
@Entity
@Table(name = "oros_order_items")
public class OrderItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "order_id", nullable = false, length = 26)
    private String orderId;

    @Column(name = "coding_system", nullable = false)
    private String codingSystem = "http://impilo.gov.zw/coding";

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "coding_version")
    private String codingVersion;

    @Column(name = "quantity", nullable = false)
    private int quantity = 1;

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "specimen_type")
    private String specimenType;

    @Column(name = "specimen_type_system")
    private String specimenTypeSystem;

    @Column(name = "body_site")
    private String bodySite;

    @Column(name = "body_site_system")
    private String bodySiteSystem;

    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // Getters and setters

    public UUID getId() { return itemId; }
    public void setId(UUID id) { this.itemId = id; }

    public UUID getItemId() { return itemId; }
    public void setItemId(UUID itemId) { this.itemId = itemId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getCodingSystem() { return codingSystem; }
    public void setCodingSystem(String codingSystem) { this.codingSystem = codingSystem; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getCodingVersion() { return codingVersion; }
    public void setCodingVersion(String codingVersion) { this.codingVersion = codingVersion; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }

    public String getSpecimenType() { return specimenType; }
    public void setSpecimenType(String specimenType) { this.specimenType = specimenType; }

    public String getSpecimenTypeSystem() { return specimenTypeSystem; }
    public void setSpecimenTypeSystem(String specimenTypeSystem) { this.specimenTypeSystem = specimenTypeSystem; }

    public String getBodySite() { return bodySite; }
    public void setBodySite(String bodySite) { this.bodySite = bodySite; }

    public String getBodySiteSystem() { return bodySiteSystem; }
    public void setBodySiteSystem(String bodySiteSystem) { this.bodySiteSystem = bodySiteSystem; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
