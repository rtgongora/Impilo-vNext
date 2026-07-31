package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "pct_cognition_mood_screens")
public class CognitionMoodScreenEntity {
    @Id @Column(name = "screen_id") private UUID screenId;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "subject_cpid") private String subjectCpid;
    @Column(name = "journey_id") private String journeyId;
    @Column(name = "encounter_id") private String encounterId;
    @Column(name = "instrument") private String instrument;
    @Column(name = "score") private BigDecimal score;
    @Column(name = "score_absent_reason") private String scoreAbsentReason;
    @Column(name = "mood_note") private String moodNote;
    @Column(name = "recorded_by") private String recordedBy;
    @Column(name = "created_at") private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (screenId == null) screenId = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getScreenId() { return screenId; }
    public void setScreenId(UUID v) { screenId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { tenantId = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { subjectCpid = v; }
    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String v) { journeyId = v; }
    public String getEncounterId() { return encounterId; }
    public void setEncounterId(String v) { encounterId = v; }
    public String getInstrument() { return instrument; }
    public void setInstrument(String v) { instrument = v; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal v) { score = v; }
    public String getScoreAbsentReason() { return scoreAbsentReason; }
    public void setScoreAbsentReason(String v) { scoreAbsentReason = v; }
    public String getMoodNote() { return moodNote; }
    public void setMoodNote(String v) { moodNote = v; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String v) { recordedBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
}
