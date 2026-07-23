package zw.gov.mohcc.impilo.msikaflow.persistence.entity;

import jakarta.persistence.*;
import zw.gov.mohcc.impilo.msikaflow.domain.StockGrade;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A per-line component of a fulfilment offer (OF-B6, Vol II §11.4 — spec
 * logical name {@code mf_fulfilment_offer_lines}).
 *
 * <p>{@code duraReservationRef} carries the DURA {@code inv_stock_reservations}
 * UUID once commitment step 5 reserved the line — DURA is the sole reservation
 * truth; this is a reference, never a second ledger.</p>
 */
@Entity
@Table(name = "mf_fulfillment_offer_lines")
public class FulfillmentOfferLineEntity {

    @Id
    @Column(name = "offer_line_id", nullable = false, length = 26)
    private String offerLineId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "offer_id", nullable = false, length = 26)
    private String offerId;

    /** Reference to the published request line (snapshot {@code lineRef}). */
    @Column(name = "request_line_ref", nullable = false, length = 64)
    private String requestLineRef;

    @Column(name = "item_code", nullable = false, length = 64)
    private String itemCode;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "stock_grade", nullable = false, length = 16)
    private StockGrade stockGrade;

    @Column(name = "substitution_proposed", nullable = false)
    private boolean substitutionProposed;

    /** DURA reservation UUID (string) once reserved at commitment step 5. */
    @Column(name = "dura_reservation_ref", length = 64)
    private String duraReservationRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public String getOfferLineId() { return offerLineId; }
    public void setOfferLineId(String offerLineId) { this.offerLineId = offerLineId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getOfferId() { return offerId; }
    public void setOfferId(String offerId) { this.offerId = offerId; }

    public String getRequestLineRef() { return requestLineRef; }
    public void setRequestLineRef(String requestLineRef) { this.requestLineRef = requestLineRef; }

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public StockGrade getStockGrade() { return stockGrade; }
    public void setStockGrade(StockGrade stockGrade) { this.stockGrade = stockGrade; }

    public boolean isSubstitutionProposed() { return substitutionProposed; }
    public void setSubstitutionProposed(boolean substitutionProposed) { this.substitutionProposed = substitutionProposed; }

    public String getDuraReservationRef() { return duraReservationRef; }
    public void setDuraReservationRef(String duraReservationRef) { this.duraReservationRef = duraReservationRef; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
