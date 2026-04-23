package zw.gov.mohcc.impilo.pacs.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "imaging_access_audit", schema = "pacs")
public class ImagingAccessAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "study_id")
    private Long studyId;

    @Column(name = "series_id")
    private Long seriesId;

    @Column(name = "instance_id")
    private Long instanceId;

    @Column(name = "actor_id", columnDefinition = "TEXT")
    private String actorId;

    @Column(name = "actor_type", columnDefinition = "TEXT")
    private String actorType;

    @Column(name = "access_type", nullable = false, length = 64)
    private String accessType;

    @Column(name = "correlation_id", columnDefinition = "TEXT")
    private String correlationId;

    @Column(name = "workflow_context", columnDefinition = "TEXT")
    private String workflowContext;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStudyId() {
        return studyId;
    }

    public void setStudyId(Long studyId) {
        this.studyId = studyId;
    }

    public Long getSeriesId() {
        return seriesId;
    }

    public void setSeriesId(Long seriesId) {
        this.seriesId = seriesId;
    }

    public Long getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(Long instanceId) {
        this.instanceId = instanceId;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getActorType() {
        return actorType;
    }

    public void setActorType(String actorType) {
        this.actorType = actorType;
    }

    public String getAccessType() {
        return accessType;
    }

    public void setAccessType(String accessType) {
        this.accessType = accessType;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getWorkflowContext() {
        return workflowContext;
    }

    public void setWorkflowContext(String workflowContext) {
        this.workflowContext = workflowContext;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
