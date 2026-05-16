package zw.gov.mohcc.impilo.nhume.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "nhume_delivery_exceptions")
public class DeliveryExceptionEntity {

    @Id
    @Column(name = "exception_id", nullable = false)
    private UUID exceptionId;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "exception_kind", nullable = false, length = 48)
    private String exceptionKind;

    @Column(name = "severity", nullable = false, length = 16)
    private String severity = "WARNING";

    @Column(name = "summary", nullable = false, length = 512)
    private String summary;

    @Column(name = "details_json", nullable = false, columnDefinition = "TEXT")
    private String detailsJson = "{}";

    @Column(name = "raised_by", length = 255)
    private String raisedBy;

    @Column(name = "raised_at", nullable = false)
    private OffsetDateTime raisedAt = OffsetDateTime.now();

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "resolved_by", length = 255)
    private String resolvedBy;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    public DeliveryExceptionEntity() {}

    public UUID getExceptionId() { return exceptionId; }
    public void setExceptionId(UUID v) { this.exceptionId = v; }
    public UUID getDeliveryId() { return deliveryId; }
    public void setDeliveryId(UUID v) { this.deliveryId = v; }
    public String getExceptionKind() { return exceptionKind; }
    public void setExceptionKind(String v) { this.exceptionKind = v; }
    public String getSeverity() { return severity; }
    public void setSeverity(String v) { this.severity = v; }
    public String getSummary() { return summary; }
    public void setSummary(String v) { this.summary = v; }
    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String v) { this.detailsJson = v; }
    public String getRaisedBy() { return raisedBy; }
    public void setRaisedBy(String v) { this.raisedBy = v; }
    public OffsetDateTime getRaisedAt() { return raisedAt; }
    public void setRaisedAt(OffsetDateTime v) { this.raisedAt = v; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime v) { this.resolvedAt = v; }
    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String v) { this.resolvedBy = v; }
    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String v) { this.resolutionNotes = v; }
}
