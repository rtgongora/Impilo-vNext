package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Referral / gatekeeping record (spec §16). */
@Entity
@Table(name = "cv_referrals")
public class ReferralEntity {

    @Id @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "pod_id", nullable = false, length = 64) private String podId = "national-spine";
    @Column(name = "coverage_id") private UUID coverageId;
    @Column(name = "member_cpid", nullable = false, length = 255) private String memberCpid;
    @Column(name = "referring_provider_id", length = 128) private String referringProviderId;
    @Column(name = "referred_provider_id", length = 128) private String referredProviderId;
    @Column(name = "referred_facility_id", length = 128) private String referredFacilityId;
    @Column(name = "clinical_referral_ref", length = 128) private String clinicalReferralRef;
    @Column(name = "scope", length = 128) private String scope;
    @Column(name = "reason", length = 255) private String reason;
    @Column(name = "permitted_visits", nullable = false) private int permittedVisits = 1;
    @Column(name = "visits_used", nullable = false) private int visitsUsed = 0;
    @Column(name = "status", nullable = false, length = 16) private String status = "ACTIVE";
    @Column(name = "valid_from", nullable = false) private LocalDate validFrom = LocalDate.now();
    @Column(name = "valid_to") private LocalDate validTo;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt = OffsetDateTime.now();

    public ReferralEntity() {}

    public static ReferralEntity create(UUID tenantId, String podId, UUID coverageId, String memberCpid) {
        ReferralEntity r = new ReferralEntity();
        r.id = UUID.randomUUID();
        r.tenantId = tenantId;
        r.podId = podId != null ? podId : "national-spine";
        r.coverageId = coverageId;
        r.memberCpid = memberCpid;
        return r;
    }

    public void touch() { this.updatedAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getPodId() { return podId; }
    public UUID getCoverageId() { return coverageId; }
    public String getMemberCpid() { return memberCpid; }
    public String getReferringProviderId() { return referringProviderId; }
    public void setReferringProviderId(String v) { this.referringProviderId = v; }
    public String getReferredProviderId() { return referredProviderId; }
    public void setReferredProviderId(String v) { this.referredProviderId = v; }
    public String getReferredFacilityId() { return referredFacilityId; }
    public void setReferredFacilityId(String v) { this.referredFacilityId = v; }
    public String getClinicalReferralRef() { return clinicalReferralRef; }
    public void setClinicalReferralRef(String v) { this.clinicalReferralRef = v; }
    public String getScope() { return scope; }
    public void setScope(String v) { this.scope = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public int getPermittedVisits() { return permittedVisits; }
    public void setPermittedVisits(int v) { this.permittedVisits = v; }
    public int getVisitsUsed() { return visitsUsed; }
    public void setVisitsUsed(int v) { this.visitsUsed = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate v) { this.validFrom = v; }
    public LocalDate getValidTo() { return validTo; }
    public void setValidTo(LocalDate v) { this.validTo = v; }
}
