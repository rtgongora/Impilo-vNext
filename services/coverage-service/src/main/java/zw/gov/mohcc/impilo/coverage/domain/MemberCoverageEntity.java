package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cv_member_coverage")
public class MemberCoverageEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pod_id", nullable = false, length = 64)
    private String podId = "national-spine";

    @Column(name = "client_id", nullable = false, length = 255)
    private String clientId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "member_number", length = 64)
    private String memberNumber;

    @Column(name = "relationship", nullable = false, length = 32)
    private String relationship = "SELF";

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    // Ruvimbo (spec §10.2): verification status is kept SEPARATE from membership status.
    @Column(name = "verification_status", nullable = false, length = 24)
    private String verificationStatus = "DECLARED";

    @Column(name = "verification_date")
    private OffsetDateTime verificationDate;

    @Column(name = "verification_source", length = 64)
    private String verificationSource;

    @Column(name = "coverage_priority", nullable = false)
    private int coveragePriority = 1;

    @Column(name = "member_category", length = 48)
    private String memberCategory;

    /** Non-null on a dependant membership → points at the principal membership (spec §10.3). */
    @Column(name = "principal_member_id")
    private UUID principalMemberId;

    @Column(name = "termination_reason", length = 255)
    private String terminationReason;

    @Column(name = "card_reference", length = 64)
    private String cardReference;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected MemberCoverageEntity() {}

    public MemberCoverageEntity(UUID tenantId, String podId, String clientId,
                                UUID planId, String memberNumber, String relationship,
                                LocalDate effectiveFrom) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.podId = podId;
        this.clientId = clientId;
        this.planId = planId;
        this.memberNumber = memberNumber;
        this.relationship = relationship;
        this.effectiveFrom = effectiveFrom;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getPodId() { return podId; }
    public String getClientId() { return clientId; }
    public UUID getPlanId() { return planId; }
    public String getMemberNumber() { return memberNumber; }
    public String getRelationship() { return relationship; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; this.updatedAt = OffsetDateTime.now(); }
    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String v) { this.verificationStatus = v; this.updatedAt = OffsetDateTime.now(); }
    public OffsetDateTime getVerificationDate() { return verificationDate; }
    public void setVerificationDate(OffsetDateTime v) { this.verificationDate = v; }
    public String getVerificationSource() { return verificationSource; }
    public void setVerificationSource(String v) { this.verificationSource = v; }
    public int getCoveragePriority() { return coveragePriority; }
    public void setCoveragePriority(int v) { this.coveragePriority = v; }
    public String getMemberCategory() { return memberCategory; }
    public void setMemberCategory(String v) { this.memberCategory = v; }
    public UUID getPrincipalMemberId() { return principalMemberId; }
    public void setPrincipalMemberId(UUID v) { this.principalMemberId = v; }
    public String getTerminationReason() { return terminationReason; }
    public void setTerminationReason(String v) { this.terminationReason = v; }
    public String getCardReference() { return cardReference; }
    public void setCardReference(String v) { this.cardReference = v; }
    public void setEffectiveTo(LocalDate v) { this.effectiveTo = v; this.updatedAt = OffsetDateTime.now(); }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
