package zw.gov.mohcc.impilo.mentalhealth.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Raises admission to PCT; never decides it. See V001__init.sql for the full rationale. */
@Entity
@Table(name = "mh_admission_request")
public class AdmissionRequestEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "referral_id", nullable = false)
    private UUID referralId;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "target_facility_id")
    private UUID targetFacilityId;

    @Column(name = "status", nullable = false)
    private String status = "RAISED";

    @Column(name = "pct_admission_id")
    private String pctAdmissionId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getReferralId() { return referralId; }
    public void setReferralId(UUID v) { this.referralId = v; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String v) { this.requestedBy = v; }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(OffsetDateTime v) { this.requestedAt = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public UUID getTargetFacilityId() { return targetFacilityId; }
    public void setTargetFacilityId(UUID v) { this.targetFacilityId = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getPctAdmissionId() { return pctAdmissionId; }
    public void setPctAdmissionId(String v) { this.pctAdmissionId = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
