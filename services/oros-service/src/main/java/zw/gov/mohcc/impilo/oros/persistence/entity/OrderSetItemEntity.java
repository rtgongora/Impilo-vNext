package zw.gov.mohcc.impilo.oros.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "oros_order_set_items")
public class OrderSetItemEntity {
    @Id @Column(name = "item_id") private UUID itemId;
    @Column(name = "set_id") private UUID setId;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "sort_order") private Integer sortOrder;
    @Column(name = "order_type") private String orderType;
    @Column(name = "zibo_code") private String ziboCode;
    @Column(name = "display_name") private String displayName;
    @Column(name = "priority") private String priority;
    @Column(name = "quantity") private Integer quantity;
    @Column(name = "instructions") private String instructions;
    @Column(name = "removable") private Boolean removable;
    @Column(name = "created_at") private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (itemId == null) itemId = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (quantity == null) quantity = 1;
        if (removable == null) removable = true;
    }

    public UUID getItemId() { return itemId; }
    public void setItemId(UUID v) { itemId = v; }
    public UUID getSetId() { return setId; }
    public void setSetId(UUID v) { setId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { tenantId = v; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer v) { sortOrder = v; }
    public String getOrderType() { return orderType; }
    public void setOrderType(String v) { orderType = v; }
    public String getZiboCode() { return ziboCode; }
    public void setZiboCode(String v) { ziboCode = v; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String v) { displayName = v; }
    public String getPriority() { return priority; }
    public void setPriority(String v) { priority = v; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer v) { quantity = v; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String v) { instructions = v; }
    public Boolean getRemovable() { return removable; }
    public void setRemovable(Boolean v) { removable = v; }
}
