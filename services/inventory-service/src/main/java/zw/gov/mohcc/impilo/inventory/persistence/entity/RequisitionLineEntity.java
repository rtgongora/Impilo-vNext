package zw.gov.mohcc.impilo.inventory.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A single line item within a requisition, tracking requested, approved,
 * and fulfilled quantities for a specific item.
 */
@Entity
@Table(name = "inv_requisition_lines")
public class RequisitionLineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "line_id", nullable = false)
    private UUID lineId;

    @Column(name = "req_id", nullable = false)
    private UUID reqId;

    @Column(name = "item_code", nullable = false)
    private String itemCode;

    @Column(name = "qty_requested", nullable = false)
    private int qtyRequested;

    @Column(name = "qty_approved")
    private Integer qtyApproved;

    @Column(name = "qty_fulfilled", nullable = false)
    private int qtyFulfilled = 0;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // Getters and setters

    public UUID getLineId() { return lineId; }
    public void setLineId(UUID lineId) { this.lineId = lineId; }

    public UUID getReqId() { return reqId; }
    public void setReqId(UUID reqId) { this.reqId = reqId; }

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }

    public int getQtyRequested() { return qtyRequested; }
    public void setQtyRequested(int qtyRequested) { this.qtyRequested = qtyRequested; }

    public Integer getQtyApproved() { return qtyApproved; }
    public void setQtyApproved(Integer qtyApproved) { this.qtyApproved = qtyApproved; }

    public int getQtyFulfilled() { return qtyFulfilled; }
    public void setQtyFulfilled(int qtyFulfilled) { this.qtyFulfilled = qtyFulfilled; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
