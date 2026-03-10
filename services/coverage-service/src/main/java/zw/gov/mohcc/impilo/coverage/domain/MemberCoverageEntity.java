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

    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

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
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
