package zw.gov.mohcc.impilo.community.social.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Health-aware community ("Maternal Health Circle", "CHW Community of Practice", ...).
 * Distinct from the CHW {@code community_units} entity in the same service.
 */
@Entity
@Table(name = "social_communities", schema = "community")
public class SocialCommunityEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "slug", nullable = false, length = 120)
    private String slug;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", nullable = false, length = 64)
    private String category;

    @Column(name = "kind", nullable = false, length = 16)
    private String kind = "public";

    @Column(name = "cover_color", length = 32)
    private String coverColor;

    @Column(name = "icon_url", columnDefinition = "TEXT")
    private String iconUrl;

    @Column(name = "cover_image_url", columnDefinition = "TEXT")
    private String coverImageUrl;

    @Column(name = "member_count", nullable = false)
    private int memberCount = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rules_json", nullable = false, columnDefinition = "jsonb")
    private String rulesJson = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "moderator_ids_json", nullable = false, columnDefinition = "jsonb")
    private String moderatorIdsJson = "[]";

    @Column(name = "nompilo_assistant_enabled", nullable = false)
    private boolean nompiloAssistantEnabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getCoverColor() { return coverColor; }
    public void setCoverColor(String coverColor) { this.coverColor = coverColor; }
    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public void setCoverImageUrl(String coverImageUrl) { this.coverImageUrl = coverImageUrl; }
    public int getMemberCount() { return memberCount; }
    public void setMemberCount(int memberCount) { this.memberCount = memberCount; }
    public String getRulesJson() { return rulesJson; }
    public void setRulesJson(String rulesJson) { this.rulesJson = rulesJson; }
    public String getModeratorIdsJson() { return moderatorIdsJson; }
    public void setModeratorIdsJson(String moderatorIdsJson) { this.moderatorIdsJson = moderatorIdsJson; }
    public boolean isNompiloAssistantEnabled() { return nompiloAssistantEnabled; }
    public void setNompiloAssistantEnabled(boolean nompiloAssistantEnabled) { this.nompiloAssistantEnabled = nompiloAssistantEnabled; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
