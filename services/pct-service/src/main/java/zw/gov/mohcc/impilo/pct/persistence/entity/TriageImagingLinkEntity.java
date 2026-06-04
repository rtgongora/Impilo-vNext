package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "pct_triage_imaging_links")
public class TriageImagingLinkEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "journey_id")
    private String journeyId;
    @Column(name = "triage_record_id")
    private Long triageRecordId;
    @Column(name = "study_id", nullable = false)
    private Long studyId;
    @Column(name = "series_id")
    private Long seriesId;
    @Column(name = "instance_id")
    private Long instanceId;
    @Column(name = "imaging_edit_id")
    private Long imagingEditId;
    @Column(name = "created_by")
    private String createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String journeyId) { this.journeyId = journeyId; }
    public Long getTriageRecordId() { return triageRecordId; }
    public void setTriageRecordId(Long triageRecordId) { this.triageRecordId = triageRecordId; }
    public Long getStudyId() { return studyId; }
    public void setStudyId(Long studyId) { this.studyId = studyId; }
    public Long getSeriesId() { return seriesId; }
    public void setSeriesId(Long seriesId) { this.seriesId = seriesId; }
    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
    public Long getImagingEditId() { return imagingEditId; }
    public void setImagingEditId(Long imagingEditId) { this.imagingEditId = imagingEditId; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
