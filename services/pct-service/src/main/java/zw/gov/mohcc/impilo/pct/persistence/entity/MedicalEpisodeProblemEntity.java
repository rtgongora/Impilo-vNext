package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Which problems a medical episode manages.
 *
 * <p>Many-to-many deliberately: an episode may manage several problems, and a problem may run
 * through several episodes over a lifetime. An {@code episode_id} column on the problem would have
 * forced the second case to duplicate the problem row.</p>
 */
@Entity
@Table(name = "pct_medical_episode_problems")
public class MedicalEpisodeProblemEntity {

    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "episode_id", nullable = false)
        private UUID episodeId;

        @Column(name = "problem_id", nullable = false)
        private UUID problemId;

        public Key() {}

        public Key(UUID episodeId, UUID problemId) {
            this.episodeId = episodeId;
            this.problemId = problemId;
        }

        public UUID getEpisodeId() { return episodeId; }
        public void setEpisodeId(UUID episodeId) { this.episodeId = episodeId; }

        public UUID getProblemId() { return problemId; }
        public void setProblemId(UUID problemId) { this.problemId = problemId; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key other)) return false;
            return Objects.equals(episodeId, other.episodeId) && Objects.equals(problemId, other.problemId);
        }

        @Override public int hashCode() { return Objects.hash(episodeId, problemId); }
    }

    @EmbeddedId
    private Key id = new Key();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /**
     * Whether this problem is why the episode exists or something managed alongside it. A
     * heart-failure admission that also manages the patient's diabetes is not a diabetes episode.
     */
    @Column(name = "role", nullable = false)
    private String role = "PRIMARY";

    @Column(name = "added_at", nullable = false)
    private OffsetDateTime addedAt = OffsetDateTime.now();

    public Key getId() { return id; }
    public void setId(Key id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public OffsetDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(OffsetDateTime addedAt) { this.addedAt = addedAt; }
}
