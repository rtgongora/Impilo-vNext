package zw.gov.mohcc.impilo.governance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "wgv_multi_site_group_member")
public class MultiSiteGroupMemberEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "member_target_type", nullable = false, length = 32)
    private String memberTargetType;

    @Column(name = "member_target_id", nullable = false, length = 128)
    private String memberTargetId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MultiSiteGroupMemberEntity() {}

    public MultiSiteGroupMemberEntity(UUID id, UUID tenantId, UUID groupId, String memberTargetType, String memberTargetId, String status) {
        this.id = id;
        this.tenantId = tenantId;
        this.groupId = groupId;
        this.memberTargetType = memberTargetType;
        this.memberTargetId = memberTargetId;
        this.status = status;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getGroupId() { return groupId; }
    public String getMemberTargetType() { return memberTargetType; }
    public String getMemberTargetId() { return memberTargetId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; touch(); }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; touch(); }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; touch(); }

    private void touch() { this.updatedAt = Instant.now(); }
}
