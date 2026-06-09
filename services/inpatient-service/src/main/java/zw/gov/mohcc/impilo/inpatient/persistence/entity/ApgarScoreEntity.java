package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "apgar_score", schema = "inpatient")
public class ApgarScoreEntity {

    @Id
    @Column(name = "apgar_id")
    private UUID apgarId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_cpid", nullable = false)
    private String subjectCpid;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "minute_mark", nullable = false)
    private Integer minuteMark;

    @Column(name = "appearance", nullable = false)
    private Integer appearance;

    @Column(name = "pulse", nullable = false)
    private Integer pulse;

    @Column(name = "grimace", nullable = false)
    private Integer grimace;

    @Column(name = "activity", nullable = false)
    private Integer activity;

    @Column(name = "respiration", nullable = false)
    private Integer respiration;

    @Column(name = "recorded_at")
    private OffsetDateTime recordedAt;

    @PrePersist
    void onCreate() {
        if (apgarId == null) apgarId = UUID.randomUUID();
        if (recordedAt == null) recordedAt = OffsetDateTime.now();
    }

    public UUID getApgarId() { return apgarId; }
    public void setApgarId(UUID apgarId) { this.apgarId = apgarId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String subjectCpid) { this.subjectCpid = subjectCpid; }
    public UUID getEncounterId() { return encounterId; }
    public void setEncounterId(UUID encounterId) { this.encounterId = encounterId; }
    public Integer getMinuteMark() { return minuteMark; }
    public void setMinuteMark(Integer minuteMark) { this.minuteMark = minuteMark; }
    public Integer getAppearance() { return appearance; }
    public void setAppearance(Integer appearance) { this.appearance = appearance; }
    public Integer getPulse() { return pulse; }
    public void setPulse(Integer pulse) { this.pulse = pulse; }
    public Integer getGrimace() { return grimace; }
    public void setGrimace(Integer grimace) { this.grimace = grimace; }
    public Integer getActivity() { return activity; }
    public void setActivity(Integer activity) { this.activity = activity; }
    public Integer getRespiration() { return respiration; }
    public void setRespiration(Integer respiration) { this.respiration = respiration; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime recordedAt) { this.recordedAt = recordedAt; }

    public int totalScore() {
        return appearance + pulse + grimace + activity + respiration;
    }
}
