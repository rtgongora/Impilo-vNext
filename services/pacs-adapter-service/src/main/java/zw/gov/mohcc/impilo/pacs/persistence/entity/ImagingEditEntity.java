package zw.gov.mohcc.impilo.pacs.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "imaging_edit", schema = "pacs")
public class ImagingEditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "study_id", nullable = false)
    private Long studyId;

    @Column(name = "series_id")
    private Long seriesId;

    @Column(name = "instance_id")
    private Long instanceId;

    @Column(name = "editor_actor_id", columnDefinition = "TEXT")
    private String editorActorId;

    @Column(name = "edit_type", nullable = false)
    private String editType = "ANNOTATION";

    @Column(name = "annotation_data", nullable = false, columnDefinition = "TEXT")
    private String annotationData;

    @Column(name = "viewport_data", columnDefinition = "TEXT")
    private String viewportData;

    @Column(name = "triage_record_id")
    private Long triageRecordId;

    @Column(name = "journey_id")
    private String journeyId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStudyId() { return studyId; }
    public void setStudyId(Long studyId) { this.studyId = studyId; }
    public Long getSeriesId() { return seriesId; }
    public void setSeriesId(Long seriesId) { this.seriesId = seriesId; }
    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
    public String getEditorActorId() { return editorActorId; }
    public void setEditorActorId(String editorActorId) { this.editorActorId = editorActorId; }
    public String getEditType() { return editType; }
    public void setEditType(String editType) { this.editType = editType; }
    public String getAnnotationData() { return annotationData; }
    public void setAnnotationData(String annotationData) { this.annotationData = annotationData; }
    public String getViewportData() { return viewportData; }
    public void setViewportData(String viewportData) { this.viewportData = viewportData; }
    public Long getTriageRecordId() { return triageRecordId; }
    public void setTriageRecordId(Long triageRecordId) { this.triageRecordId = triageRecordId; }
    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String journeyId) { this.journeyId = journeyId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
