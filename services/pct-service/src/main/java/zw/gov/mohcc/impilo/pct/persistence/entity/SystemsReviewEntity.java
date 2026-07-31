package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "pct_systems_reviews")
public class SystemsReviewEntity {
    @Id @Column(name = "review_id") private UUID reviewId;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "subject_cpid") private String subjectCpid;
    @Column(name = "journey_id") private String journeyId;
    @Column(name = "encounter_id") private String encounterId;
    @Column(name = "systems_json") private String systemsJson;
    @Column(name = "recorded_by") private String recordedBy;
    @Column(name = "created_at") private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (reviewId == null) reviewId = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getReviewId() { return reviewId; }
    public void setReviewId(UUID v) { reviewId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { tenantId = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { subjectCpid = v; }
    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String v) { journeyId = v; }
    public String getEncounterId() { return encounterId; }
    public void setEncounterId(String v) { encounterId = v; }
    public String getSystemsJson() { return systemsJson; }
    public void setSystemsJson(String v) { systemsJson = v; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String v) { recordedBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
}
