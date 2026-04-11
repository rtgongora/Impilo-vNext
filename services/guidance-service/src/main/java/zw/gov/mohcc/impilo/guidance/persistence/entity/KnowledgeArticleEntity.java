package zw.gov.mohcc.impilo.guidance.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "knowledge_articles", schema = "guidance")
public class KnowledgeArticleEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false) private String tenantId;
    @Column(nullable = false, length = 512) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String summary;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(nullable = false, length = 64) private String category;
    @Column(nullable = false, length = 64) private String domain;
    @Column(columnDefinition = "JSONB") private String tags;
    @Column(length = 255) private String source;
    @Column(name = "source_url", length = 1024) private String sourceUrl;
    @Column(nullable = false, length = 32) private String status = "PUBLISHED";
    @Column(name = "relevance_context", columnDefinition = "JSONB") private String relevanceContext;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    protected KnowledgeArticleEntity() {}

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getContent() { return content; }
    public String getCategory() { return category; }
    public String getDomain() { return domain; }
    public String getSource() { return source; }
    public String getSourceUrl() { return sourceUrl; }
    public String getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
