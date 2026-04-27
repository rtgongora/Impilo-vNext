package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.costa.domain.enums.BillingTimingMode;
import zw.gov.mohcc.impilo.costa.domain.enums.ClaimPackStatus;

import java.time.OffsetDateTime;

@Entity
@Table(name = "costa_claim_packs")
public class ClaimPackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_id", nullable = false, length = 26)
    private String billId;

    @Column(name = "insurer_ref", length = 100)
    private String insurerRef;

    @Column(name = "claim_type", nullable = false, length = 30)
    private String claimType = "INITIAL";

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload = "{}";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ClaimPackStatus status = ClaimPackStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_timing_mode", length = 40)
    private BillingTimingMode billingTimingMode;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "response", columnDefinition = "jsonb")
    private String response;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() { if (createdAt == null) createdAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBillId() { return billId; }
    public void setBillId(String billId) { this.billId = billId; }
    public String getInsurerRef() { return insurerRef; }
    public void setInsurerRef(String insurerRef) { this.insurerRef = insurerRef; }
    public String getClaimType() { return claimType; }
    public void setClaimType(String claimType) { this.claimType = claimType; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public ClaimPackStatus getStatus() { return status; }
    public void setStatus(ClaimPackStatus status) { this.status = status; }
    public OffsetDateTime getSentAt() { return sentAt; }
    public void setSentAt(OffsetDateTime sentAt) { this.sentAt = sentAt; }
    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public BillingTimingMode getBillingTimingMode() {
        return billingTimingMode;
    }

    public void setBillingTimingMode(BillingTimingMode billingTimingMode) {
        this.billingTimingMode = billingTimingMode;
    }
}
