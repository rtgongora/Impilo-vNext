package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "costa_patient_transactions")
public class PatientTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "txn_id", nullable = false, unique = true)
    private UUID txnId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_cpid", nullable = false, length = 128)
    private String patientCpid;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "txn_type", nullable = false, length = 32)
    private String txnType;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "encounter_id", length = 128)
    private String encounterId;

    @Column(name = "bill_id", length = 128)
    private String billId;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "gross_amount", precision = 14, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "insurer_amount", precision = 14, scale = 2)
    private BigDecimal insurerAmount = BigDecimal.ZERO;

    @Column(name = "patient_amount", precision = 14, scale = 2)
    private BigDecimal patientAmount;

    @Column(name = "payment_method", length = 32)
    private String paymentMethod;

    @Column(name = "payment_ref", length = 128)
    private String paymentRef;

    @Column(name = "txn_date", nullable = false)
    private OffsetDateTime txnDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (txnId == null) {
            txnId = UUID.randomUUID();
        }
        if (txnDate == null) {
            txnDate = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTxnId() { return txnId; }
    public void setTxnId(UUID txnId) { this.txnId = txnId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getPatientCpid() { return patientCpid; }
    public void setPatientCpid(String patientCpid) { this.patientCpid = patientCpid; }
    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }
    public String getTxnType() { return txnType; }
    public void setTxnType(String txnType) { this.txnType = txnType; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public String getEncounterId() { return encounterId; }
    public void setEncounterId(String encounterId) { this.encounterId = encounterId; }
    public String getBillId() { return billId; }
    public void setBillId(String billId) { this.billId = billId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public void setGrossAmount(BigDecimal grossAmount) { this.grossAmount = grossAmount; }
    public BigDecimal getInsurerAmount() { return insurerAmount; }
    public void setInsurerAmount(BigDecimal insurerAmount) { this.insurerAmount = insurerAmount; }
    public BigDecimal getPatientAmount() { return patientAmount; }
    public void setPatientAmount(BigDecimal patientAmount) { this.patientAmount = patientAmount; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getPaymentRef() { return paymentRef; }
    public void setPaymentRef(String paymentRef) { this.paymentRef = paymentRef; }
    public OffsetDateTime getTxnDate() { return txnDate; }
    public void setTxnDate(OffsetDateTime txnDate) { this.txnDate = txnDate; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
