package zw.gov.mohcc.impilo.msika.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;

@Entity
@Table(name = "msika_catalog_items")
public class CatalogItemEntity {

    @Id
    @Column(name = "item_id", length = 26)
    private String itemId;

    @Column(name = "catalog_id", nullable = false, length = 26)
    private String catalogId;

    @Column(name = "kind", nullable = false, length = 30)
    private String kind;

    @Column(name = "canonical_code", nullable = false, length = 100)
    private String canonicalCode;

    @Column(name = "display_name", nullable = false, length = 500)
    private String displayName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "synonyms", columnDefinition = "TEXT[]")
    private String[] synonyms;

    @Column(name = "tags", columnDefinition = "TEXT[]")
    private String[] tags;

    @Column(name = "sourcing_category_code", length = 64)
    private String sourcingCategoryCode;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "restrictions", columnDefinition = "jsonb")
    private String restrictions;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "zibo_bindings", columnDefinition = "jsonb")
    private String ziboBindings;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    private int lockVersion;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        if (restrictions == null) restrictions = "{}";
        if (ziboBindings == null) ziboBindings = "[]";
        if (metadata == null) metadata = "{}";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public String getId() { return itemId; }
    public void setId(String id) { this.itemId = id; }

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public String getCatalogId() { return catalogId; }
    public void setCatalogId(String catalogId) { this.catalogId = catalogId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getCanonicalCode() { return canonicalCode; }
    public void setCanonicalCode(String canonicalCode) { this.canonicalCode = canonicalCode; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String[] getSynonyms() { return synonyms; }
    public void setSynonyms(String[] synonyms) { this.synonyms = synonyms; }
    public String[] getTags() { return tags; }
    public String getSourcingCategoryCode() { return sourcingCategoryCode; }
    public void setSourcingCategoryCode(String sourcingCategoryCode) { this.sourcingCategoryCode = sourcingCategoryCode; }
    public void setTags(String[] tags) { this.tags = tags; }
    public String getRestrictions() { return restrictions; }
    public void setRestrictions(String restrictions) { this.restrictions = restrictions; }
    public String getZiboBindings() { return ziboBindings; }
    public void setZiboBindings(String ziboBindings) { this.ziboBindings = ziboBindings; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public int getLockVersion() { return lockVersion; }
    public void setLockVersion(int lockVersion) { this.lockVersion = lockVersion; }
}
