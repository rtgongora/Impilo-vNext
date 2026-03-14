package zw.gov.mohcc.impilo.datawarehouse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dwh_materializer_watermark")
public class MaterializerWatermarkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "watermark_id")
    private Long watermarkId;

    @Column(name = "source_table", nullable = false, unique = true, length = 128)
    private String sourceTable;

    @Column(name = "last_receipt_id")
    private UUID lastReceiptId;

    @Column(name = "last_stored_at")
    private OffsetDateTime lastStoredAt;

    @Column(name = "processed_count", nullable = false)
    private long processedCount;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public MaterializerWatermarkEntity() {}

    // Getters
    public Long getWatermarkId() { return watermarkId; }
    public String getSourceTable() { return sourceTable; }
    public UUID getLastReceiptId() { return lastReceiptId; }
    public OffsetDateTime getLastStoredAt() { return lastStoredAt; }
    public long getProcessedCount() { return processedCount; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    // Setters
    public void setWatermarkId(Long watermarkId) { this.watermarkId = watermarkId; }
    public void setSourceTable(String sourceTable) { this.sourceTable = sourceTable; }
    public void setLastReceiptId(UUID lastReceiptId) { this.lastReceiptId = lastReceiptId; }
    public void setLastStoredAt(OffsetDateTime lastStoredAt) { this.lastStoredAt = lastStoredAt; }
    public void setProcessedCount(long processedCount) { this.processedCount = processedCount; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
