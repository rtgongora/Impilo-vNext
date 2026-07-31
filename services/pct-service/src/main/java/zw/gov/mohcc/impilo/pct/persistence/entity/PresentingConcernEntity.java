package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "pct_presenting_concerns")
public class PresentingConcernEntity {
    @Id @Column(name = "concern_id") private UUID concernId;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "subject_cpid") private String subjectCpid;
    @Column(name = "journey_id") private String journeyId;
    @Column(name = "encounter_id") private String encounterId;
    @Column(name = "concern_text") private String concernText;
    @Column(name = "onset_at") private OffsetDateTime onsetAt;
    @Column(name = "timeline_json") private String timelineJson;
    @Column(name = "source") private String source;
    @Column(name = "recorded_by") private String recordedBy;
    @Column(name = "created_at") private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (concernId == null) concernId = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (source == null) source = "AMBULATORY";
    }

    public UUID getConcernId() { return concernId; }
    public void setConcernId(UUID v) { concernId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { tenantId = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { subjectCpid = v; }
    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String v) { journeyId = v; }
    public String getEncounterId() { return encounterId; }
    public void setEncounterId(String v) { encounterId = v; }
    public String getConcernText() { return concernText; }
    public void setConcernText(String v) { concernText = v; }
    public OffsetDateTime getOnsetAt() { return onsetAt; }
    public void setOnsetAt(OffsetDateTime v) { onsetAt = v; }
    public String getTimelineJson() { return timelineJson; }
    public void setTimelineJson(String v) { timelineJson = v; }
    public String getSource() { return source; }
    public void setSource(String v) { source = v; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String v) { recordedBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
}
