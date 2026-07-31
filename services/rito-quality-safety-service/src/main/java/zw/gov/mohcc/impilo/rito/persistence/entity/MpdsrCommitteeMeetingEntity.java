package zw.gov.mohcc.impilo.rito.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rit_mpdsr_committee_meeting", schema = "rito")
public class MpdsrCommitteeMeetingEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "review_id", nullable = false)
    private UUID reviewId;

    @Column(name = "meeting_at", nullable = false)
    private OffsetDateTime meetingAt;

    @Column(name = "review_level", nullable = false, length = 32)
    private String reviewLevel;

    @Column(name = "chair_actor_id", length = 128)
    private String chairActorId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "members_json", nullable = false, columnDefinition = "jsonb")
    private String membersJson;

    @Column(name = "minutes_summary", columnDefinition = "TEXT")
    private String minutesSummary;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (membersJson == null) membersJson = "[]";
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getReviewId() { return reviewId; }
    public void setReviewId(UUID reviewId) { this.reviewId = reviewId; }
    public OffsetDateTime getMeetingAt() { return meetingAt; }
    public void setMeetingAt(OffsetDateTime meetingAt) { this.meetingAt = meetingAt; }
    public String getReviewLevel() { return reviewLevel; }
    public void setReviewLevel(String reviewLevel) { this.reviewLevel = reviewLevel; }
    public String getChairActorId() { return chairActorId; }
    public void setChairActorId(String chairActorId) { this.chairActorId = chairActorId; }
    public String getMembersJson() { return membersJson; }
    public void setMembersJson(String membersJson) { this.membersJson = membersJson; }
    public String getMinutesSummary() { return minutesSummary; }
    public void setMinutesSummary(String minutesSummary) { this.minutesSummary = minutesSummary; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
