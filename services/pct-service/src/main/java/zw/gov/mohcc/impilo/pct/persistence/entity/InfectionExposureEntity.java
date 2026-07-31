package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "pct_infection_exposures")
public class InfectionExposureEntity {
    @Id @Column(name = "exposure_id") private UUID exposureId;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "subject_cpid") private String subjectCpid;
    @Column(name = "journey_id") private String journeyId;
    @Column(name = "encounter_id") private String encounterId;
    @Column(name = "exposure_type") private String exposureType;
    @Column(name = "detail") private String detail;
    @Column(name = "exposed_on") private LocalDate exposedOn;
    @Column(name = "recorded_by") private String recordedBy;
    @Column(name = "created_at") private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (exposureId == null) exposureId = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getExposureId() { return exposureId; }
    public void setExposureId(UUID v) { exposureId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { tenantId = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { subjectCpid = v; }
    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String v) { journeyId = v; }
    public String getEncounterId() { return encounterId; }
    public void setEncounterId(String v) { encounterId = v; }
    public String getExposureType() { return exposureType; }
    public void setExposureType(String v) { exposureType = v; }
    public String getDetail() { return detail; }
    public void setDetail(String v) { detail = v; }
    public LocalDate getExposedOn() { return exposedOn; }
    public void setExposedOn(LocalDate v) { exposedOn = v; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String v) { recordedBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
}
