package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ed_trauma_survey")
public class EdTraumaSurveyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trauma_id", nullable = false)
    private UUID traumaId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Canonical trauma-episode correlation (W5). */
    @Column(name = "trauma_episode_id")
    private UUID traumaEpisodeId;

    @Column(name = "survey_type", nullable = false)
    private String surveyType;

    @Column(name = "section_key")
    private String sectionKey;

    // Bind as JSON so PostgreSQL accepts the String into the jsonb column (avoids the 42804
    // jsonb-as-varchar 500 — same fix as ed_trauma_activation.team_assignments).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "checklist", columnDefinition = "jsonb")
    private String checklistJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "vitals", columnDefinition = "jsonb")
    private String vitalsJson = "{}";

    /** Amendment lineage: the survey this row amends (new injuries found later), if any. */
    @Column(name = "amends_survey_id")
    private Long amendsSurveyId;

    @Column(name = "revision", nullable = false)
    private int revision = 1;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "recorded_by", nullable = false)
    private String recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    @PrePersist
    protected void onCreate() {
        if (recordedAt == null) recordedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getTraumaId() { return traumaId; }
    public void setTraumaId(UUID traumaId) { this.traumaId = traumaId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getSurveyType() { return surveyType; }
    public void setSurveyType(String surveyType) { this.surveyType = surveyType; }
    public String getSectionKey() { return sectionKey; }
    public void setSectionKey(String sectionKey) { this.sectionKey = sectionKey; }
    public String getChecklistJson() { return checklistJson; }
    public void setChecklistJson(String checklistJson) { this.checklistJson = checklistJson; }
    public String getVitalsJson() { return vitalsJson; }
    public void setVitalsJson(String vitalsJson) { this.vitalsJson = vitalsJson; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public UUID getTraumaEpisodeId() { return traumaEpisodeId; }
    public void setTraumaEpisodeId(UUID traumaEpisodeId) { this.traumaEpisodeId = traumaEpisodeId; }
    public Long getAmendsSurveyId() { return amendsSurveyId; }
    public void setAmendsSurveyId(Long amendsSurveyId) { this.amendsSurveyId = amendsSurveyId; }
    public int getRevision() { return revision; }
    public void setRevision(int revision) { this.revision = revision; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
