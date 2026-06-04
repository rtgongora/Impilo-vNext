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
@Table(name = "nhume_delivery_items")
public class DeliveryItemEntity {

    @Id
    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo = 1;

    @Column(name = "item_kind", nullable = false, length = 32)
    private String itemKind;

    @Column(name = "item_ref", length = 255)
    private String itemRef;

    @Column(name = "description", nullable = false, length = 512)
    private String description;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "unit_of_measure", length = 32)
    private String unitOfMeasure;

    @Column(name = "package_type", length = 64)
    private String packageType;

    @Column(name = "weight_grams")
    private BigDecimal weightGrams;

    @Column(name = "volume_ml")
    private BigDecimal volumeMl;

    @Column(name = "cold_chain", nullable = false)
    private boolean coldChain;

    @Column(name = "controlled", nullable = false)
    private boolean controlled;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public DeliveryItemEntity() {}

    public UUID getItemId() { return itemId; }
    public void setItemId(UUID v) { this.itemId = v; }
    public UUID getDeliveryId() { return deliveryId; }
    public void setDeliveryId(UUID v) { this.deliveryId = v; }
    public int getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(int v) { this.sequenceNo = v; }
    public String getItemKind() { return itemKind; }
    public void setItemKind(String v) { this.itemKind = v; }
    public String getItemRef() { return itemRef; }
    public void setItemRef(String v) { this.itemRef = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal v) { this.quantity = v; }
    public String getUnitOfMeasure() { return unitOfMeasure; }
    public void setUnitOfMeasure(String v) { this.unitOfMeasure = v; }
    public String getPackageType() { return packageType; }
    public void setPackageType(String v) { this.packageType = v; }
    public BigDecimal getWeightGrams() { return weightGrams; }
    public void setWeightGrams(BigDecimal v) { this.weightGrams = v; }
    public BigDecimal getVolumeMl() { return volumeMl; }
    public void setVolumeMl(BigDecimal v) { this.volumeMl = v; }
    public boolean isColdChain() { return coldChain; }
    public void setColdChain(boolean v) { this.coldChain = v; }
    public boolean isControlled() { return controlled; }
    public void setControlled(boolean v) { this.controlled = v; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String v) { this.metadataJson = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
