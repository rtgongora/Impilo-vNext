package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Payer Registry (spec §8) — an organisation that may financially cover care.
 * A SUSPENDED payer blocks new enrolments but keeps historic transactions accessible.
 */
@Entity
@Table(name = "cv_payers")
public class PayerEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pod_id", nullable = false, length = 64)
    private String podId = "national-spine";

    @Column(name = "payer_code", nullable = false, length = 64)
    private String payerCode;

    @Column(name = "legal_name", nullable = false, length = 255)
    private String legalName;

    @Column(name = "trading_name", length = 255)
    private String tradingName;

    @Column(name = "organisation_type", nullable = false, length = 48)
    private String organisationType = "MEDICAL_AID";

    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contacts_json", columnDefinition = "jsonb")
    private String contactsJson;

    @Column(name = "integration_method", nullable = false, length = 16)
    private String integrationMethod = "MANUAL";

    @Column(name = "settlement_reference", length = 128)
    private String settlementReference;

    @Column(name = "regulatory_status", length = 64)
    private String regulatoryStatus;

    @Column(name = "source_authority", length = 128)
    private String sourceAuthority;

    @Column(name = "approved_by", length = 128)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom = LocalDate.now();

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "version", nullable = false)
    private long version = 0;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public PayerEntity() {}

    public static PayerEntity create(UUID tenantId, String podId, String payerCode, String legalName,
                                     String tradingName, String organisationType, String integrationMethod) {
        PayerEntity p = new PayerEntity();
        p.id = UUID.randomUUID();
        p.tenantId = tenantId;
        p.podId = podId != null ? podId : "national-spine";
        p.payerCode = payerCode;
        p.legalName = legalName;
        p.tradingName = tradingName;
        if (organisationType != null && !organisationType.isBlank()) p.organisationType = organisationType;
        if (integrationMethod != null && !integrationMethod.isBlank()) p.integrationMethod = integrationMethod;
        return p;
    }

    public void touch() { this.updatedAt = OffsetDateTime.now(); this.version++; }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getPodId() { return podId; }
    public String getPayerCode() { return payerCode; }
    public String getLegalName() { return legalName; }
    public void setLegalName(String v) { this.legalName = v; }
    public String getTradingName() { return tradingName; }
    public void setTradingName(String v) { this.tradingName = v; }
    public String getOrganisationType() { return organisationType; }
    public void setOrganisationType(String v) { this.organisationType = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getContactsJson() { return contactsJson; }
    public void setContactsJson(String v) { this.contactsJson = v; }
    public String getIntegrationMethod() { return integrationMethod; }
    public void setIntegrationMethod(String v) { this.integrationMethod = v; }
    public String getSettlementReference() { return settlementReference; }
    public void setSettlementReference(String v) { this.settlementReference = v; }
    public String getRegulatoryStatus() { return regulatoryStatus; }
    public void setRegulatoryStatus(String v) { this.regulatoryStatus = v; }
    public String getSourceAuthority() { return sourceAuthority; }
    public void setSourceAuthority(String v) { this.sourceAuthority = v; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String v) { this.approvedBy = v; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(OffsetDateTime v) { this.approvedAt = v; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate v) { this.effectiveTo = v; }
    public long getVersion() { return version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
