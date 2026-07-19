package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Product — a marketed/contracted package within a scheme (spec §9). */
@Entity
@Table(name = "cv_products")
public class ProductEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pod_id", nullable = false, length = 64)
    private String podId = "national-spine";

    @Column(name = "scheme_ref", nullable = false)
    private UUID schemeRef;

    @Column(name = "product_code", nullable = false, length = 64)
    private String productCode;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public ProductEntity() {}

    public static ProductEntity create(UUID tenantId, String podId, UUID schemeRef,
                                       String productCode, String name) {
        ProductEntity p = new ProductEntity();
        p.id = UUID.randomUUID();
        p.tenantId = tenantId;
        p.podId = podId != null ? podId : "national-spine";
        p.schemeRef = schemeRef;
        p.productCode = productCode;
        p.name = name;
        return p;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getPodId() { return podId; }
    public UUID getSchemeRef() { return schemeRef; }
    public String getProductCode() { return productCode; }
    public String getName() { return name; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
}
