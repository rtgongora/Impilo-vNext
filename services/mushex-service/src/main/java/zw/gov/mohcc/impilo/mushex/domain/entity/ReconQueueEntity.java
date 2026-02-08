package zw.gov.mohcc.impilo.mushex.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import zw.gov.mohcc.impilo.mushex.domain.enums.ReconStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mushex_recon_queue")
public class ReconQueueEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "statement_ref", nullable = false)
    private String statementRef;

    @Column(name = "statement_date", nullable = false)
    private LocalDate statementDate;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "counterparty")
    private String counterparty;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReconStatus status;

    @Column(name = "matched_intent_id")
    private String matchedIntentId;

    @Column(name = "imported_at", nullable = false)
    private OffsetDateTime importedAt;

    @Column(name = "matched_at")
    private OffsetDateTime matchedAt;

    @PrePersist
    protected void onCreate() {
        if (this.importedAt == null) {
            this.importedAt = OffsetDateTime.now();
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getStatementRef() {
        return statementRef;
    }

    public void setStatementRef(String statementRef) {
        this.statementRef = statementRef;
    }

    public LocalDate getStatementDate() {
        return statementDate;
    }

    public void setStatementDate(LocalDate statementDate) {
        this.statementDate = statementDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCounterparty() {
        return counterparty;
    }

    public void setCounterparty(String counterparty) {
        this.counterparty = counterparty;
    }

    public ReconStatus getStatus() {
        return status;
    }

    public void setStatus(ReconStatus status) {
        this.status = status;
    }

    public String getMatchedIntentId() {
        return matchedIntentId;
    }

    public void setMatchedIntentId(String matchedIntentId) {
        this.matchedIntentId = matchedIntentId;
    }

    public OffsetDateTime getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(OffsetDateTime importedAt) {
        this.importedAt = importedAt;
    }

    public OffsetDateTime getMatchedAt() {
        return matchedAt;
    }

    public void setMatchedAt(OffsetDateTime matchedAt) {
        this.matchedAt = matchedAt;
    }
}
