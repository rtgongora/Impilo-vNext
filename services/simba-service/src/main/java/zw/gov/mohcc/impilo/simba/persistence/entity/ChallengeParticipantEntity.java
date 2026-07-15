package zw.gov.mohcc.impilo.simba.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "challenge_participants", schema = "simba")
public class ChallengeParticipantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "challenge_id", nullable = false)
    private UUID challengeId;

    @Column(name = "person_cpid", nullable = false, length = 128)
    private String personCpid;

    @Column(name = "progress_value", precision = 10, scale = 2)
    private BigDecimal progressValue = BigDecimal.ZERO;

    @Column(name = "shared_to_feed", nullable = false)
    private boolean sharedToFeed = false;

    @Column(name = "share_post_id")
    private UUID sharePostId;

    @Column(name = "rank")
    private Integer rank;

    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt;

    @PrePersist
    protected void onCreate() {
        if (joinedAt == null) {
            joinedAt = OffsetDateTime.now();
        }
        if (progressValue == null) {
            progressValue = BigDecimal.ZERO;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(UUID challengeId) {
        this.challengeId = challengeId;
    }

    public String getPersonCpid() {
        return personCpid;
    }

    public void setPersonCpid(String personCpid) {
        this.personCpid = personCpid;
    }

    public BigDecimal getProgressValue() {
        return progressValue;
    }

    public void setProgressValue(BigDecimal progressValue) {
        this.progressValue = progressValue;
    }

    public boolean isSharedToFeed() {
        return sharedToFeed;
    }

    public void setSharedToFeed(boolean sharedToFeed) {
        this.sharedToFeed = sharedToFeed;
    }

    public UUID getSharePostId() {
        return sharePostId;
    }

    public void setSharePostId(UUID sharePostId) {
        this.sharePostId = sharePostId;
    }

    public Integer getRank() {
        return rank;
    }

    public void setRank(Integer rank) {
        this.rank = rank;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(OffsetDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }
}
