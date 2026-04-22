package zw.gov.mohcc.impilo.msikaflow.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.msikaflow.domain.FulfillmentMode;
import zw.gov.mohcc.impilo.msikaflow.domain.LineItemKind;

import java.time.OffsetDateTime;

@Entity
@Table(name = "mf_cart_items")
public class CartItemEntity {

    @Id
    @Column(name = "id", nullable = false, length = 26)
    private String id;

    @Column(name = "cart_id", nullable = false, length = 26)
    private String cartId;

    @Column(name = "msika_core_code", nullable = false, length = 128)
    private String msikaCoreCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private LineItemKind kind = LineItemKind.PRODUCT;

    @Column(name = "qty", nullable = false)
    private int qty = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_mode", length = 30)
    private FulfillmentMode fulfillmentMode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCartId() { return cartId; }
    public void setCartId(String cartId) { this.cartId = cartId; }
    public String getMsikaCoreCode() { return msikaCoreCode; }
    public void setMsikaCoreCode(String msikaCoreCode) { this.msikaCoreCode = msikaCoreCode; }
    public LineItemKind getKind() { return kind; }
    public void setKind(LineItemKind kind) { this.kind = kind; }
    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }
    public FulfillmentMode getFulfillmentMode() { return fulfillmentMode; }
    public void setFulfillmentMode(FulfillmentMode fulfillmentMode) { this.fulfillmentMode = fulfillmentMode; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}

