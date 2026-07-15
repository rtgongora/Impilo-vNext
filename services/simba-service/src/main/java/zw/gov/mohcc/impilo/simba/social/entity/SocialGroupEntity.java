package zw.gov.mohcc.impilo.simba.social.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A wellness-social group (supersedes clubs via group_kind='CLUB'). */
@Entity
@Table(name = "simba_group", schema = "simba")
public class SocialGroupEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false, unique = true)
    private UUID groupId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "group_kind", nullable = false, length = 24)
    private String groupKind = "GROUP";

    @Column(name = "topic", length = 64)
    private String topic;

    @Column(name = "sensitive_flag", nullable = false)
    private boolean sensitiveFlag = false;

    @Column(name = "community_id")
    private UUID communityId;

    @Column(name = "programme_id")
    private UUID programmeId;

    @Column(name = "visibility", nullable = false, length = 24)
    private String visibility = "PUBLIC";

    @Column(name = "join_policy", nullable = false, length = 16)
    private String joinPolicy = "OPEN";

    @Column(name = "max_members")
    private Integer maxMembers;

    @Column(name = "member_count", nullable = false)
    private int memberCount = 0;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (groupId == null) {
            groupId = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getGroupId() { return groupId; }
    public void setGroupId(UUID groupId) { this.groupId = groupId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getGroupKind() { return groupKind; }
    public void setGroupKind(String groupKind) { this.groupKind = groupKind; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public boolean isSensitiveFlag() { return sensitiveFlag; }
    public void setSensitiveFlag(boolean sensitiveFlag) { this.sensitiveFlag = sensitiveFlag; }
    public UUID getCommunityId() { return communityId; }
    public void setCommunityId(UUID communityId) { this.communityId = communityId; }
    public UUID getProgrammeId() { return programmeId; }
    public void setProgrammeId(UUID programmeId) { this.programmeId = programmeId; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public String getJoinPolicy() { return joinPolicy; }
    public void setJoinPolicy(String joinPolicy) { this.joinPolicy = joinPolicy; }
    public Integer getMaxMembers() { return maxMembers; }
    public void setMaxMembers(Integer maxMembers) { this.maxMembers = maxMembers; }
    public int getMemberCount() { return memberCount; }
    public void setMemberCount(int memberCount) { this.memberCount = memberCount; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
