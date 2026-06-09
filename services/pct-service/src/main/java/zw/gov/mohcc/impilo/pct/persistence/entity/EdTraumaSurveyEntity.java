package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
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

    @Column(name = "survey_type", nullable = false)
    private String surveyType;

    @Column(name = "section_key")
    private String sectionKey;

    @Column(name = "checklist", columnDefinition = "jsonb")
    private String checklistJson = "{}";

    @Column(name = "vitals", columnDefinition = "jsonb")
    private String vitalsJson = "{}";

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
}
