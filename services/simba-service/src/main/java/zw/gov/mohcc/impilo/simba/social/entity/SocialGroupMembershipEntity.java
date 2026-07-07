package zw.gov.mohcc.impilo.simba.social.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Membership of a wellness-social group with role + posting/comment permissions. */
@Entity
@Table(name = "simba_group_membership", schema = "simba")
public class SocialGroupMembershipEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "membership_id", nullable = false, unique = true)
    private UUID membershipId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "person_cpid", nullable = false, length = 128)
    private String personCpid;

    @Column(name = "role", nullable = false, length = 24)
    private String role = "MEMBER";

    @Column(name = "can_post", nullable = false)
    private boolean canPost = true;

    @Column(name = "can_comment", nullable = false)
    private boolean canComment = true;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "joined_at", nullable = false, updatable = false)
    private OffsetDateTime joinedAt;

    @PrePersist
    void onCreate() {
        if (membershipId == null) {
            membershipId = UUID.randomUUID();
        }
        joinedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getMembershipId() { return membershipId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getGroupId() { return groupId; }
    public void setGroupId(UUID groupId) { this.groupId = groupId; }
    public String getPersonCpid() { return personCpid; }
    public void setPersonCpid(String personCpid) { this.personCpid = personCpid; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public boolean isCanPost() { return canPost; }
    public void setCanPost(boolean canPost) { this.canPost = canPost; }
    public boolean isCanComment() { return canComment; }
    public void setCanComment(boolean canComment) { this.canComment = canComment; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getJoinedAt() { return joinedAt; }
}
