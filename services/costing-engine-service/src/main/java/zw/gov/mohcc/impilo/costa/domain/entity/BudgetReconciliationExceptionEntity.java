package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** An unmatched / mismatched actual or commitment routed for finance review. */
@Entity
@Table(name = "costa_budget_reconciliation_exception")
public class BudgetReconciliationExceptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exception_id", nullable = false, unique = true)
    private UUID exceptionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "budget_id")
    private UUID budgetId;

    @Column(name = "budget_line_id")
    private UUID budgetLineId;

    @Column(name = "exception_type", nullable = false, length = 28)
    private String exceptionType;

    @Column(name = "source", length = 20)
    private String source;

    @Column(name = "source_reference", length = 160)
    private String sourceReference;

    @Column(name = "expected", precision = 18, scale = 2)
    private BigDecimal expected;

    @Column(name = "observed", precision = 18, scale = 2)
    private BigDecimal observed;

    @Column(name = "delta", precision = 18, scale = 2)
    private BigDecimal delta;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "OPEN";

    @Column(name = "resolution_note")
    private String resolutionNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (exceptionId == null) exceptionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public UUID getExceptionId() { return exceptionId; }
    public void setExceptionId(UUID exceptionId) { this.exceptionId = exceptionId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getBudgetId() { return budgetId; }
    public void setBudgetId(UUID budgetId) { this.budgetId = budgetId; }
    public UUID getBudgetLineId() { return budgetLineId; }
    public void setBudgetLineId(UUID budgetLineId) { this.budgetLineId = budgetLineId; }
    public String getExceptionType() { return exceptionType; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getSourceReference() { return sourceReference; }
    public void setSourceReference(String sourceReference) { this.sourceReference = sourceReference; }
    public BigDecimal getExpected() { return expected; }
    public void setExpected(BigDecimal expected) { this.expected = expected; }
    public BigDecimal getObserved() { return observed; }
    public void setObserved(BigDecimal observed) { this.observed = observed; }
    public BigDecimal getDelta() { return delta; }
    public void setDelta(BigDecimal delta) { this.delta = delta; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
