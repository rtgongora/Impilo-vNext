package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A reference to actual expenditure posted against a budget line. This is a
 * pointer + amount only — the authoritative record lives with the owner
 * (MusheX payment, COSTA invoice, or an imported ERP document). Idempotent on
 * (tenant, source, sourceReference).
 */
@Entity
@Table(name = "costa_budget_actual_reference")
public class BudgetActualReferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actual_ref_id", nullable = false, unique = true)
    private UUID actualRefId;

    @Column(name = "budget_line_id", nullable = false)
    private UUID budgetLineId;

    @Column(name = "budget_id", nullable = false)
    private UUID budgetId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "source", nullable = false, length = 20)
    private String source;

    @Column(name = "source_reference", nullable = false, length = 160)
    private String sourceReference;

    @Column(name = "commitment_id")
    private UUID commitmentId;

    @Column(name = "actual_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal actualAmount;

    @Column(name = "ingest_source", nullable = false, length = 12)
    private String ingestSource = "API";

    @Column(name = "is_reversed", nullable = false)
    private boolean reversed = false;

    @Column(name = "reversal_of")
    private UUID reversalOf;

    @Column(name = "posted_at", nullable = false)
    private OffsetDateTime postedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (actualRefId == null) actualRefId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (postedAt == null) postedAt = now;
        if (createdAt == null) createdAt = now;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getActualRefId() { return actualRefId; }
    public void setActualRefId(UUID actualRefId) { this.actualRefId = actualRefId; }
    public UUID getBudgetLineId() { return budgetLineId; }
    public void setBudgetLineId(UUID budgetLineId) { this.budgetLineId = budgetLineId; }
    public UUID getBudgetId() { return budgetId; }
    public void setBudgetId(UUID budgetId) { this.budgetId = budgetId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getSourceReference() { return sourceReference; }
    public void setSourceReference(String sourceReference) { this.sourceReference = sourceReference; }
    public UUID getCommitmentId() { return commitmentId; }
    public void setCommitmentId(UUID commitmentId) { this.commitmentId = commitmentId; }
    public BigDecimal getActualAmount() { return actualAmount; }
    public void setActualAmount(BigDecimal actualAmount) { this.actualAmount = actualAmount; }
    public String getIngestSource() { return ingestSource; }
    public void setIngestSource(String ingestSource) { this.ingestSource = ingestSource; }
    public boolean isReversed() { return reversed; }
    public void setReversed(boolean reversed) { this.reversed = reversed; }
    public UUID getReversalOf() { return reversalOf; }
    public void setReversalOf(UUID reversalOf) { this.reversalOf = reversalOf; }
    public OffsetDateTime getPostedAt() { return postedAt; }
    public void setPostedAt(OffsetDateTime postedAt) { this.postedAt = postedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
