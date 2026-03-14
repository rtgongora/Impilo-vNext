package zw.gov.mohcc.impilo.support.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "sup_knowledge_articles")
public class KnowledgeArticleEntity {
    @Id @Column(name = "article_id") private UUID articleId;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(nullable = false, length = 500) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String body;
    @Column(nullable = false, length = 64) private String category = "GENERAL";
    @Column(nullable = false, length = 32) private String status = "DRAFT";
    @Column(name = "author_ref", nullable = false) private String authorRef;
    @Column(columnDefinition = "TEXT") private String tags;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
    @Column(name = "published_at") private OffsetDateTime publishedAt;
    @Column(nullable = false) private int version = 1;

    protected KnowledgeArticleEntity() {}
    public KnowledgeArticleEntity(UUID articleId, UUID tenantId, String title, String body, String authorRef) {
        this.articleId = articleId; this.tenantId = tenantId; this.title = title;
        this.body = body; this.authorRef = authorRef;
        OffsetDateTime now = OffsetDateTime.now(); this.createdAt = now; this.updatedAt = now;
    }

    public UUID getArticleId() { return articleId; }
    public UUID getTenantId() { return tenantId; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getBody() { return body; }
    public void setBody(String v) { this.body = v; }
    public String getCategory() { return category; }
    public void setCategory(String v) { this.category = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getAuthorRef() { return authorRef; }
    public String getTags() { return tags; }
    public void setTags(String v) { this.tags = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime v) { this.publishedAt = v; }
    public int getVersion() { return version; }
    public void setVersion(int v) { this.version = v; }
}
