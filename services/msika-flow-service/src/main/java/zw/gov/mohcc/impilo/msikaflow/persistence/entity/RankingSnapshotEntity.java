package zw.gov.mohcc.impilo.msikaflow.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * OF-B29 — the §11.1/§21 ranked-position + label snapshot
 * ({@code mf_ranking_snapshots}) backing the ranking-transparency audit.
 * Captured best-effort at sweep time while the request is live; one row per
 * (request, offer), refreshed on re-capture. A committed selection whose
 * offer has no snapshot is flagged RANKING_SNAPSHOT_MISSING — absence is a
 * finding, never a silent skip.
 */
@Entity
@Table(name = "mf_ranking_snapshots")
public class RankingSnapshotEntity {

    @Id
    @Column(name = "snapshot_id", nullable = false, length = 26)
    private String snapshotId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "request_id", nullable = false, length = 26)
    private String requestId;

    @Column(name = "offer_id", nullable = false, length = 26)
    private String offerId;

    /** 1-based position in the declared comparison sort (§4.5: completeness, then price). */
    @Column(name = "position", nullable = false)
    private int position;

    /** JSON array of ranked-because labels, e.g. ["SUITABLE","CHEAPEST"]. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ranked_because_json", nullable = false, columnDefinition = "jsonb")
    private String rankedBecauseJson;

    @Column(name = "price_total", precision = 14, scale = 2)
    private BigDecimal priceTotal;

    @Column(name = "captured_at", nullable = false)
    private OffsetDateTime capturedAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (capturedAt == null) {
            capturedAt = OffsetDateTime.now();
        }
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public String getSnapshotId() { return snapshotId; }
    public void setSnapshotId(String snapshotId) { this.snapshotId = snapshotId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getOfferId() { return offerId; }
    public void setOfferId(String offerId) { this.offerId = offerId; }

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }

    public String getRankedBecauseJson() { return rankedBecauseJson; }
    public void setRankedBecauseJson(String rankedBecauseJson) { this.rankedBecauseJson = rankedBecauseJson; }

    public BigDecimal getPriceTotal() { return priceTotal; }
    public void setPriceTotal(BigDecimal priceTotal) { this.priceTotal = priceTotal; }

    public OffsetDateTime getCapturedAt() { return capturedAt; }
    public void setCapturedAt(OffsetDateTime capturedAt) { this.capturedAt = capturedAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
