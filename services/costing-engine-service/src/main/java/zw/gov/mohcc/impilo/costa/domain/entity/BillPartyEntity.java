package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;
import zw.gov.mohcc.impilo.costa.domain.enums.PartyType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "costa_bill_parties")
public class BillPartyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_id", nullable = false, length = 26)
    private String billId;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_type", nullable = false, length = 20)
    private PartyType partyType;

    @Column(name = "party_ref", length = 100)
    private String partyRef;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason_codes", columnDefinition = "text[]")
    private String[] reasonCodes = {};

    @Column(name = "trace", nullable = false, columnDefinition = "jsonb")
    private String trace = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() { if (createdAt == null) createdAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBillId() { return billId; }
    public void setBillId(String billId) { this.billId = billId; }
    public PartyType getPartyType() { return partyType; }
    public void setPartyType(PartyType partyType) { this.partyType = partyType; }
    public String getPartyRef() { return partyRef; }
    public void setPartyRef(String partyRef) { this.partyRef = partyRef; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String[] getReasonCodes() { return reasonCodes; }
    public void setReasonCodes(String[] reasonCodes) { this.reasonCodes = reasonCodes; }
    public String getTrace() { return trace; }
    public void setTrace(String trace) { this.trace = trace; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
