package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Authorisation line — service-level detail with an independent decision (spec §17.3). */
@Entity
@Table(name = "cv_authorisation_lines")
public class AuthorisationLineEntity {

    @Id @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "authorisation_id", nullable = false) private UUID authorisationId;
    @Column(name = "benefit_code", nullable = false, length = 64) private String benefitCode;
    @Column(name = "service_code", length = 64) private String serviceCode;
    @Column(name = "description", length = 255) private String description;
    @Column(name = "quantity_requested", nullable = false) private BigDecimal quantityRequested = BigDecimal.ONE;
    @Column(name = "quantity_approved", nullable = false) private BigDecimal quantityApproved = BigDecimal.ZERO;
    @Column(name = "quantity_consumed", nullable = false) private BigDecimal quantityConsumed = BigDecimal.ZERO;
    @Column(name = "estimated_amount") private BigDecimal estimatedAmount;
    @Column(name = "approved_amount") private BigDecimal approvedAmount;
    @Column(name = "line_status", nullable = false, length = 24) private String lineStatus = "REQUESTED";
    @Column(name = "reason", length = 255) private String reason;
    @Column(name = "reservation_ref", length = 128) private String reservationRef;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt = OffsetDateTime.now();

    public AuthorisationLineEntity() {}

    public static AuthorisationLineEntity create(UUID tenantId, UUID authorisationId, String benefitCode) {
        AuthorisationLineEntity l = new AuthorisationLineEntity();
        l.id = UUID.randomUUID();
        l.tenantId = tenantId;
        l.authorisationId = authorisationId;
        l.benefitCode = benefitCode;
        return l;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getAuthorisationId() { return authorisationId; }
    public String getBenefitCode() { return benefitCode; }
    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String v) { this.serviceCode = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public BigDecimal getQuantityRequested() { return quantityRequested; }
    public void setQuantityRequested(BigDecimal v) { this.quantityRequested = v; }
    public BigDecimal getQuantityApproved() { return quantityApproved; }
    public void setQuantityApproved(BigDecimal v) { this.quantityApproved = v; }
    public BigDecimal getQuantityConsumed() { return quantityConsumed; }
    public void setQuantityConsumed(BigDecimal v) { this.quantityConsumed = v; }
    public BigDecimal getEstimatedAmount() { return estimatedAmount; }
    public void setEstimatedAmount(BigDecimal v) { this.estimatedAmount = v; }
    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public void setApprovedAmount(BigDecimal v) { this.approvedAmount = v; }
    public String getLineStatus() { return lineStatus; }
    public void setLineStatus(String v) { this.lineStatus = v; this.updatedAt = OffsetDateTime.now(); }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public String getReservationRef() { return reservationRef; }
    public void setReservationRef(String v) { this.reservationRef = v; }
}
