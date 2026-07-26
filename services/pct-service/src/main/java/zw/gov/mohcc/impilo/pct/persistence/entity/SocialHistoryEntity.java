package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** One social-history finding. risk_level NULL means not assessed and never defaults. See V106__structured_history.sql. */
@Entity
@Table(name = "pct_social_history")
public class SocialHistoryEntity {

    @Id
    @Column(name = "entry_id")
    private UUID entryId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "subject_cpid")
    private String subjectCpid;

    @Column(name = "category")
    private String category;

    @Column(name = "status")
    private String status;

    @Column(name = "detail")
    private String detail;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column(name = "recorded_by")
    private String recordedBy;

    @Column(name = "last_updated")
    private LocalDate lastUpdated;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (entryId == null) entryId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getEntryId() { return entryId; }
    public void setEntryId(UUID v) { this.entryId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { this.subjectCpid = v; }
    public String getCategory() { return category; }
    public void setCategory(String v) { this.category = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getDetail() { return detail; }
    public void setDetail(String v) { this.detail = v; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String v) { this.riskLevel = v; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String v) { this.recordedBy = v; }
    public LocalDate getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDate v) { this.lastUpdated = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
