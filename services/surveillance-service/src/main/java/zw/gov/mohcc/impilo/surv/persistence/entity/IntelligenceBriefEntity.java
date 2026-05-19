package zw.gov.mohcc.impilo.surv.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import zw.gov.mohcc.impilo.surv.core.BriefStatus;

@Entity
@Table(name = "intelligence_briefs", schema = "surv")
public class IntelligenceBriefEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "brief_type", nullable = false)
    private String briefType = "SITUATION";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BriefStatus status = BriefStatus.DRAFT;

    @Column(name = "summary_markdown")
    private String summaryMarkdown;

    @Column(name = "generated_by", nullable = false)
    private String generatedBy;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBriefType() { return briefType; }
    public void setBriefType(String briefType) { this.briefType = briefType; }
    public BriefStatus getStatus() { return status; }
    public void setStatus(BriefStatus status) { this.status = status; }
    public String getSummaryMarkdown() { return summaryMarkdown; }
    public void setSummaryMarkdown(String summaryMarkdown) { this.summaryMarkdown = summaryMarkdown; }
    public String getGeneratedBy() { return generatedBy; }
    public void setGeneratedBy(String generatedBy) { this.generatedBy = generatedBy; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
