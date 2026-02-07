package zw.gov.mohcc.impilo.inventory.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A single line within a stock count session, recording the system quantity,
 * counted quantity, and computed variance for a specific item/batch/expiry.
 */
@Entity
@Table(name = "inv_count_lines")
public class CountLineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "line_id", nullable = false)
    private UUID lineId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "item_code", nullable = false)
    private String itemCode;

    @Column(name = "batch")
    private String batch;

    @Column(name = "expiry")
    private LocalDate expiry;

    @Column(name = "bin_id")
    private UUID binId;

    @Column(name = "qty_system", nullable = false)
    private int qtySystem = 0;

    @Column(name = "qty_counted")
    private Integer qtyCounted;

    @Column(name = "variance")
    private Integer variance;

    @Column(name = "barcode_scanned")
    private String barcodeScanned;

    @Column(name = "counted_at")
    private OffsetDateTime countedAt;

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

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public UUID getBinId() { return binId; }
    public void setBinId(UUID binId) { this.binId = binId; }

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }

    public String getBatch() { return batch; }
    public void setBatch(String batch) { this.batch = batch; }

    public LocalDate getExpiry() { return expiry; }
    public void setExpiry(LocalDate expiry) { this.expiry = expiry; }

    public int getQtySystem() { return qtySystem; }
    public void setQtySystem(int qtySystem) { this.qtySystem = qtySystem; }

    public Integer getQtyCounted() { return qtyCounted; }
    public void setQtyCounted(Integer qtyCounted) { this.qtyCounted = qtyCounted; }

    public Integer getVariance() { return variance; }
    public void setVariance(Integer variance) { this.variance = variance; }

    public String getBarcodeScanned() { return barcodeScanned; }
    public void setBarcodeScanned(String barcodeScanned) { this.barcodeScanned = barcodeScanned; }

    public OffsetDateTime getCountedAt() { return countedAt; }
    public void setCountedAt(OffsetDateTime countedAt) { this.countedAt = countedAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
