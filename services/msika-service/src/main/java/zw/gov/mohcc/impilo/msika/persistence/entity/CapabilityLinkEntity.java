package zw.gov.mohcc.impilo.msika.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "msika_capability_links")
public class CapabilityLinkEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "item_id", nullable = false, length = 26)
    private String itemId;

    @Column(name = "capability_type", nullable = false, length = 20)
    private String capabilityType;

    @Column(name = "applies_to_scope", columnDefinition = "jsonb")
    private String appliesToScope;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public String getCapabilityType() { return capabilityType; }
    public void setCapabilityType(String capabilityType) { this.capabilityType = capabilityType; }
    public String getAppliesToScope() { return appliesToScope; }
    public void setAppliesToScope(String appliesToScope) { this.appliesToScope = appliesToScope; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
