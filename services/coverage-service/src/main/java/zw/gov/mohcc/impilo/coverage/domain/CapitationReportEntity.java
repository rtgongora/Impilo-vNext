package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Capitation encounter report (spec §5, §19). */
@Entity
@Table(name = "cv_capitation_reports")
public class CapitationReportEntity {

    @Id @Column(name = "id", nullable = false) private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "pod_id", nullable = false, length = 64) private String podId = "national-spine";
    @Column(name = "provider_id", nullable = false, length = 128) private String providerId;
    @Column(name = "facility_id", length = 128) private String facilityId;
    @Column(name = "payer_id", length = 255) private String payerId;
    @Column(name = "period_start", nullable = false) private LocalDate periodStart;
    @Column(name = "period_end", nullable = false) private LocalDate periodEnd;
    @Column(name = "member_count", nullable = false) private int memberCount = 0;
    @Column(name = "encounter_count", nullable = false) private int encounterCount = 0;
    @Column(name = "capitation_amount", nullable = false) private BigDecimal capitationAmount = BigDecimal.ZERO;
    @Column(name = "currency", nullable = false, length = 8) private String currency = "USD";
    @Column(name = "status", nullable = false, length = 16) private String status = "SUBMITTED";
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();

    public CapitationReportEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public void setPodId(String v) { this.podId = v; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String v) { this.providerId = v; }
    public String getFacilityId() { return facilityId; }
    public void setFacilityId(String v) { this.facilityId = v; }
    public String getPayerId() { return payerId; }
    public void setPayerId(String v) { this.payerId = v; }
    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate v) { this.periodStart = v; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate v) { this.periodEnd = v; }
    public int getMemberCount() { return memberCount; }
    public void setMemberCount(int v) { this.memberCount = v; }
    public int getEncounterCount() { return encounterCount; }
    public void setEncounterCount(int v) { this.encounterCount = v; }
    public BigDecimal getCapitationAmount() { return capitationAmount; }
    public void setCapitationAmount(BigDecimal v) { this.capitationAmount = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
