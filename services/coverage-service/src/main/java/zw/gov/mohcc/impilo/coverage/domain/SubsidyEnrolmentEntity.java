package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Member ↔ subsidy-programme link, with an optional per-member annual-cap override. */
@Entity
@Table(name = "cv_subsidy_enrolments")
public class SubsidyEnrolmentEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pod_id", nullable = false, length = 64)
    private String podId = "national-spine";

    @Column(name = "subsidy_program_id", nullable = false)
    private UUID subsidyProgramId;

    @Column(name = "member_cpid", nullable = false, length = 80)
    private String memberCpid;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "annual_cap_override")
    private BigDecimal annualCapOverride;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "enrolled_by", length = 120)
    private String enrolledBy;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (effectiveFrom == null) effectiveFrom = LocalDate.now();
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getPodId() { return podId; }
    public void setPodId(String podId) { this.podId = podId; }
    public UUID getSubsidyProgramId() { return subsidyProgramId; }
    public void setSubsidyProgramId(UUID subsidyProgramId) { this.subsidyProgramId = subsidyProgramId; }
    public String getMemberCpid() { return memberCpid; }
    public void setMemberCpid(String memberCpid) { this.memberCpid = memberCpid; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getAnnualCapOverride() { return annualCapOverride; }
    public void setAnnualCapOverride(BigDecimal annualCapOverride) { this.annualCapOverride = annualCapOverride; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getEnrolledBy() { return enrolledBy; }
    public void setEnrolledBy(String enrolledBy) { this.enrolledBy = enrolledBy; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
