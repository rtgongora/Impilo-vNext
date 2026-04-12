package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "costa_patient_accounts",
        uniqueConstraints = @UniqueConstraint(name = "uq_costa_patient_account", columnNames = {"tenant_id", "patient_cpid"}))
public class PatientAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false, unique = true)
    private UUID accountId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_cpid", nullable = false, length = 128)
    private String patientCpid;

    @Column(name = "total_billed", precision = 14, scale = 2)
    private BigDecimal totalBilled = BigDecimal.ZERO;

    @Column(name = "total_paid", precision = 14, scale = 2)
    private BigDecimal totalPaid = BigDecimal.ZERO;

    @Column(name = "total_insurer", precision = 14, scale = 2)
    private BigDecimal totalInsurer = BigDecimal.ZERO;

    @Column(name = "total_outstanding", precision = 14, scale = 2)
    private BigDecimal totalOutstanding = BigDecimal.ZERO;

    @Column(name = "total_writeoff", precision = 14, scale = 2)
    private BigDecimal totalWriteoff = BigDecimal.ZERO;

    @Column(name = "currency", length = 3)
    private String currency = "ZWL";

    @Column(name = "last_activity")
    private OffsetDateTime lastActivity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (accountId == null) {
            accountId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getPatientCpid() { return patientCpid; }
    public void setPatientCpid(String patientCpid) { this.patientCpid = patientCpid; }
    public BigDecimal getTotalBilled() { return totalBilled; }
    public void setTotalBilled(BigDecimal totalBilled) { this.totalBilled = totalBilled; }
    public BigDecimal getTotalPaid() { return totalPaid; }
    public void setTotalPaid(BigDecimal totalPaid) { this.totalPaid = totalPaid; }
    public BigDecimal getTotalInsurer() { return totalInsurer; }
    public void setTotalInsurer(BigDecimal totalInsurer) { this.totalInsurer = totalInsurer; }
    public BigDecimal getTotalOutstanding() { return totalOutstanding; }
    public void setTotalOutstanding(BigDecimal totalOutstanding) { this.totalOutstanding = totalOutstanding; }
    public BigDecimal getTotalWriteoff() { return totalWriteoff; }
    public void setTotalWriteoff(BigDecimal totalWriteoff) { this.totalWriteoff = totalWriteoff; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public OffsetDateTime getLastActivity() { return lastActivity; }
    public void setLastActivity(OffsetDateTime lastActivity) { this.lastActivity = lastActivity; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
