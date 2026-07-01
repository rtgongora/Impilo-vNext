package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A budget revision / reprogramming request that spawns a new version when applied. */
@Entity
@Table(name = "costa_budget_revision")
public class BudgetRevisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "revision_id", nullable = false, unique = true)
    private UUID revisionId;

    @Column(name = "budget_id", nullable = false)
    private UUID budgetId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "from_version_id")
    private UUID fromVersionId;

    @Column(name = "to_version_id")
    private UUID toVersionId;

    @Column(name = "revision_type", nullable = false, length = 20)
    private String revisionType;

    @Column(name = "reason")
    private String reason;

    @Column(name = "net_change", nullable = false, precision = 18, scale = 2)
    private BigDecimal netChange = BigDecimal.ZERO;

    @Column(name = "requested_by", length = 128)
    private String requestedBy;

    @Column(name = "approved_by", length = 128)
    private String approvedBy;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "REQUESTED";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (revisionId == null) revisionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public UUID getRevisionId() { return revisionId; }
    public void setRevisionId(UUID revisionId) { this.revisionId = revisionId; }
    public UUID getBudgetId() { return budgetId; }
    public void setBudgetId(UUID budgetId) { this.budgetId = budgetId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getFromVersionId() { return fromVersionId; }
    public void setFromVersionId(UUID fromVersionId) { this.fromVersionId = fromVersionId; }
    public UUID getToVersionId() { return toVersionId; }
    public void setToVersionId(UUID toVersionId) { this.toVersionId = toVersionId; }
    public String getRevisionType() { return revisionType; }
    public void setRevisionType(String revisionType) { this.revisionType = revisionType; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public BigDecimal getNetChange() { return netChange; }
    public void setNetChange(BigDecimal netChange) { this.netChange = netChange; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
