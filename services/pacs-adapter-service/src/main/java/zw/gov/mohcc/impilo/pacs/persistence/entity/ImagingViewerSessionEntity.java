package zw.gov.mohcc.impilo.pacs.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "imaging_viewer_session", schema = "pacs")
public class ImagingViewerSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "study_id", nullable = false)
    private Long studyId;

    @Column(name = "launched_by", columnDefinition = "TEXT")
    private String launchedBy;

    @Column(name = "launched_at", nullable = false)
    private OffsetDateTime launchedAt = OffsetDateTime.now();

    @Column(name = "session_status", nullable = false, length = 32)
    private String sessionStatus = "ACTIVE";

    @Column(name = "viewer_type", nullable = false, length = 64)
    private String viewerType = "DICOMWEB_STACK";

    @Column(name = "context_ref", columnDefinition = "TEXT")
    private String contextRef;

    @Column(name = "scope", columnDefinition = "TEXT")
    private String scope;

    @Column(name = "correlation_id", columnDefinition = "TEXT")
    private String correlationId;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

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

    public String getLaunchedBy() {
        return launchedBy;
    }

    public void setLaunchedBy(String launchedBy) {
        this.launchedBy = launchedBy;
    }

    public OffsetDateTime getLaunchedAt() {
        return launchedAt;
    }

    public void setLaunchedAt(OffsetDateTime launchedAt) {
        this.launchedAt = launchedAt;
    }

    public String getSessionStatus() {
        return sessionStatus;
    }

    public void setSessionStatus(String sessionStatus) {
        this.sessionStatus = sessionStatus;
    }

    public String getViewerType() {
        return viewerType;
    }

    public void setViewerType(String viewerType) {
        this.viewerType = viewerType;
    }

    public String getContextRef() {
        return contextRef;
    }

    public void setContextRef(String contextRef) {
        this.contextRef = contextRef;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
}
