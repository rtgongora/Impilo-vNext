package zw.gov.mohcc.impilo.msika.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "msika_external_mappings")
public class ExternalMappingEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "source_id", nullable = false, length = 26)
    private String sourceId;

    @Column(name = "external_code", nullable = false, length = 200)
    private String externalCode;

    @Column(name = "external_name", length = 500)
    private String externalName;

    @Column(name = "internal_item_id", length = 26)
    private String internalItemId;

    @Column(name = "map_type", nullable = false, length = 10)
    private String mapType;

    @Column(name = "confidence", precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "reviewed_by", length = 128)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        if (status == null) status = "PENDING";
        if (mapType == null) mapType = "EXACT";
        if (confidence == null) confidence = BigDecimal.ZERO;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public String getExternalCode() { return externalCode; }
    public void setExternalCode(String externalCode) { this.externalCode = externalCode; }
    public String getExternalName() { return externalName; }
    public void setExternalName(String externalName) { this.externalName = externalName; }
    public String getInternalItemId() { return internalItemId; }
    public void setInternalItemId(String internalItemId) { this.internalItemId = internalItemId; }
    public String getMapType() { return mapType; }
    public void setMapType(String mapType) { this.mapType = mapType; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(OffsetDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
