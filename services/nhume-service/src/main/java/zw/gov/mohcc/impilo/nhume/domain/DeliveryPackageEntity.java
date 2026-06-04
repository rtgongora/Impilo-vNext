package zw.gov.mohcc.impilo.nhume.domain;

import jakarta.persistence.Column;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "nhume_delivery_packages")
public class DeliveryPackageEntity {

    @Id
    @Column(name = "package_id", nullable = false)
    private UUID packageId;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "package_code", nullable = false, length = 64)
    private String packageCode;

    @Column(name = "package_type", length = 64)
    private String packageType;

    @Column(name = "seal_id", length = 128)
    private String sealId;

    @Column(name = "weight_grams")
    private BigDecimal weightGrams;

    @Column(name = "cold_chain", nullable = false)
    private boolean coldChain;

    @Column(name = "fragile", nullable = false)
    private boolean fragile;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public DeliveryPackageEntity() {}

    public UUID getPackageId() { return packageId; }
    public void setPackageId(UUID v) { this.packageId = v; }
    public UUID getDeliveryId() { return deliveryId; }
    public void setDeliveryId(UUID v) { this.deliveryId = v; }
    public String getPackageCode() { return packageCode; }
    public void setPackageCode(String v) { this.packageCode = v; }
    public String getPackageType() { return packageType; }
    public void setPackageType(String v) { this.packageType = v; }
    public String getSealId() { return sealId; }
    public void setSealId(String v) { this.sealId = v; }
    public BigDecimal getWeightGrams() { return weightGrams; }
    public void setWeightGrams(BigDecimal v) { this.weightGrams = v; }
    public boolean isColdChain() { return coldChain; }
    public void setColdChain(boolean v) { this.coldChain = v; }
    public boolean isFragile() { return fragile; }
    public void setFragile(boolean v) { this.fragile = v; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String v) { this.metadataJson = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
