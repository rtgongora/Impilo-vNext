package zw.gov.mohcc.impilo.madi.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "blood_products", schema = "madi")
public class BloodProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, unique = true)
    private UUID productId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

@Column(name = "product_code", nullable = false)
    private String productCode;

@Column(name = "product_name", nullable = false)
    private String productName;

@Column(name = "component_type", nullable = false)
    private String componentType;

@Column(name = "blood_group", nullable = false)
    private String bloodGroup;

@Column(name = "shelf_life_days")
    private Integer shelfLifeDays;

@Column(name = "status")
    private String status;

@Column(name = "jurisdiction")
    private String jurisdiction;

@Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (productId == null) {
            productId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getComponentType() { return componentType; }
    public void setComponentType(String componentType) { this.componentType = componentType; }

    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }

    public Integer getShelfLifeDays() { return shelfLifeDays; }
    public void setShelfLifeDays(Integer shelfLifeDays) { this.shelfLifeDays = shelfLifeDays; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

}