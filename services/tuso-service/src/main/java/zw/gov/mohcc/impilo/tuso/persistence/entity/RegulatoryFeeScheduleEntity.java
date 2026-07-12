package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A governed regulatory fee-schedule row (SI 78 of 2017 and successors). The
 * platform records the SCHEDULE STRUCTURE; the {@code amount} is NULL until a
 * governed configuration supplies the statutory value — amounts are never
 * invented. Versioned; status PENDING_REGULATOR_APPROVAL → ACTIVE → RETIRED.
 */
@Entity
@Table(name = "regulatory_fee_schedule", schema = "tuso")
public class RegulatoryFeeScheduleEntity {

    @Id
    @Column(name = "fee_id", nullable = false, updatable = false)
    private UUID feeId;

    @Column(name = "schedule_code", nullable = false, length = 64)
    private String scheduleCode;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "fee_code", nullable = false, length = 64)
    private String feeCode;

    @Column(name = "applies_to_catalogue_code", nullable = false, length = 64)
    private String appliesToCatalogueCode;

    @Column(name = "applies_to_class_code", length = 16)
    private String appliesToClassCode;

    @Column(name = "amount", precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "source_instrument", nullable = false, length = 128)
    private String sourceInstrument;

    @Column(name = "source_section", length = 64)
    private String sourceSection;

    @Column(name = "status", nullable = false, length = 64)
    private String status = "PENDING_REGULATOR_APPROVAL";

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "approved_by", length = 255)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @PrePersist
    void prePersist() {
        if (feeId == null) feeId = UUID.randomUUID();
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public UUID getFeeId() { return feeId; }
    public void setFeeId(UUID feeId) { this.feeId = feeId; }
    public String getScheduleCode() { return scheduleCode; }
    public void setScheduleCode(String scheduleCode) { this.scheduleCode = scheduleCode; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getFeeCode() { return feeCode; }
    public void setFeeCode(String feeCode) { this.feeCode = feeCode; }
    public String getAppliesToCatalogueCode() { return appliesToCatalogueCode; }
    public void setAppliesToCatalogueCode(String appliesToCatalogueCode) { this.appliesToCatalogueCode = appliesToCatalogueCode; }
    public String getAppliesToClassCode() { return appliesToClassCode; }
    public void setAppliesToClassCode(String appliesToClassCode) { this.appliesToClassCode = appliesToClassCode; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getSourceInstrument() { return sourceInstrument; }
    public void setSourceInstrument(String sourceInstrument) { this.sourceInstrument = sourceInstrument; }
    public String getSourceSection() { return sourceSection; }
    public void setSourceSection(String sourceSection) { this.sourceSection = sourceSection; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
}
