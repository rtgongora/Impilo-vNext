package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cv_subsidy_programs")
public class SubsidyProgramEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "program_code", nullable = false, length = 64)
    private String programCode;

    @Column(name = "program_name", nullable = false, length = 255)
    private String programName;

    @Column(name = "payer_id", length = 255)
    private String payerId;

    @Column(name = "subsidy_type", nullable = false, length = 32)
    private String subsidyType;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "annual_cap")
    private BigDecimal annualCap;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    protected SubsidyProgramEntity() {}

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getProgramCode() { return programCode; }
    public String getProgramName() { return programName; }
    public String getPayerId() { return payerId; }
    public String getSubsidyType() { return subsidyType; }
    public String getStatus() { return status; }
    public BigDecimal getAnnualCap() { return annualCap; }
    public String getCurrency() { return currency; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
}
