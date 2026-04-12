package zw.gov.mohcc.impilo.simba.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "wellness_profiles", schema = "simba")
public class WellnessProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false, unique = true)
    private UUID profileId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "person_cpid", nullable = false, length = 128)
    private String personCpid;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(name = "goals_json", columnDefinition = "jsonb")
    private String goalsJson;

    @Column(name = "preferences_json", columnDefinition = "jsonb")
    private String preferencesJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (profileId == null) {
            profileId = UUID.randomUUID();
        }
        createdAt = now;
        updatedAt = now;
        if (goalsJson == null) {
            goalsJson = "{}";
        }
        if (preferencesJson == null) {
            preferencesJson = "{}";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public void setProfileId(UUID profileId) {
        this.profileId = profileId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getPersonCpid() {
        return personCpid;
    }

    public void setPersonCpid(String personCpid) {
        this.personCpid = personCpid;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getGoalsJson() {
        return goalsJson;
    }

    public void setGoalsJson(String goalsJson) {
        this.goalsJson = goalsJson;
    }

    public String getPreferencesJson() {
        return preferencesJson;
    }

    public void setPreferencesJson(String preferencesJson) {
        this.preferencesJson = preferencesJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
