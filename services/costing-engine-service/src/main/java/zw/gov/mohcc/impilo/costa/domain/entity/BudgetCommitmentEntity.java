package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A budget commitment — an obligation reserved against a budget line before
 * payment. {@code ownerReference} points at the owning system's PO / requisition
 * / contract; COSTA never duplicates that record.
 */
@Entity
@Table(name = "costa_budget_commitment")
public class BudgetCommitmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "commitment_id", nullable = false, unique = true)
    private UUID commitmentId;

    @Column(name = "budget_line_id", nullable = false)
    private UUID budgetLineId;

    @Column(name = "budget_id", nullable = false)
    private UUID budgetId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "owner_system", nullable = false, length = 16)
    private String ownerSystem;

    @Column(name = "owner_reference", nullable = false, length = 128)
    private String ownerReference;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "committed_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal committedAmount;

    @Column(name = "obligated_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal obligatedAmount = BigDecimal.ZERO;

    @Column(name = "liquidated_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal liquidatedAmount = BigDecimal.ZERO;

    @Column(name = "status", nullable = false, length = 24)
    private String status = "COMMITTED";

    @Column(name = "committed_at", nullable = false)
    private OffsetDateTime committedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (commitmentId == null) commitmentId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (committedAt == null) committedAt = now;
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    /** Amount still committed but not yet turned into actual expenditure. */
    public BigDecimal outstanding() {
        BigDecimal c = committedAmount != null ? committedAmount : BigDecimal.ZERO;
        BigDecimal l = liquidatedAmount != null ? liquidatedAmount : BigDecimal.ZERO;
        BigDecimal remaining = c.subtract(l);
        return remaining.signum() < 0 ? BigDecimal.ZERO : remaining;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getCommitmentId() { return commitmentId; }
    public void setCommitmentId(UUID commitmentId) { this.commitmentId = commitmentId; }
    public UUID getBudgetLineId() { return budgetLineId; }
    public void setBudgetLineId(UUID budgetLineId) { this.budgetLineId = budgetLineId; }
    public UUID getBudgetId() { return budgetId; }
    public void setBudgetId(UUID budgetId) { this.budgetId = budgetId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getOwnerSystem() { return ownerSystem; }
    public void setOwnerSystem(String ownerSystem) { this.ownerSystem = ownerSystem; }
    public String getOwnerReference() { return ownerReference; }
    public void setOwnerReference(String ownerReference) { this.ownerReference = ownerReference; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getCommittedAmount() { return committedAmount; }
    public void setCommittedAmount(BigDecimal committedAmount) { this.committedAmount = committedAmount; }
    public BigDecimal getObligatedAmount() { return obligatedAmount; }
    public void setObligatedAmount(BigDecimal obligatedAmount) { this.obligatedAmount = obligatedAmount; }
    public BigDecimal getLiquidatedAmount() { return liquidatedAmount; }
    public void setLiquidatedAmount(BigDecimal liquidatedAmount) { this.liquidatedAmount = liquidatedAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCommittedAt() { return committedAt; }
    public void setCommittedAt(OffsetDateTime committedAt) { this.committedAt = committedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
