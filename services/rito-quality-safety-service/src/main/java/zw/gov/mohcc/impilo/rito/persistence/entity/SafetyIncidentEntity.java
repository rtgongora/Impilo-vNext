package zw.gov.mohcc.impilo.rito.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rit_safety_incident", schema = "rito")
public class SafetyIncidentEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "incident_type", nullable = false, length = 64)
    private String incidentType;

    @Column(name = "harm_level", nullable = false, length = 32)
    private String harmLevel;

    @Column(name = "occurred_at")
    private OffsetDateTime occurredAt;

    @Column(name = "location_detail", length = 512)
    private String locationDetail;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contributing_factors", columnDefinition = "jsonb")
    private String contributingFactorsJson;

    @Column(name = "immediate_actions", columnDefinition = "TEXT")
    private String immediateActions;

    @Column(name = "rca_method", length = 64)
    private String rcaMethod;

    @Column(name = "rca_summary", columnDefinition = "TEXT")
    private String rcaSummary;

    @Column(name = "sentinel", nullable = false)
    private Boolean sentinel;

    @Column(name = "reported_within_sla")
    private Boolean reportedWithinSla;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID caseId) { this.caseId = caseId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getIncidentType() { return incidentType; }
    public void setIncidentType(String incidentType) { this.incidentType = incidentType; }
    public String getHarmLevel() { return harmLevel; }
    public void setHarmLevel(String harmLevel) { this.harmLevel = harmLevel; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }
    public String getLocationDetail() { return locationDetail; }
    public void setLocationDetail(String locationDetail) { this.locationDetail = locationDetail; }
    public String getContributingFactorsJson() { return contributingFactorsJson; }
    public void setContributingFactorsJson(String contributingFactorsJson) { this.contributingFactorsJson = contributingFactorsJson; }
    public String getImmediateActions() { return immediateActions; }
    public void setImmediateActions(String immediateActions) { this.immediateActions = immediateActions; }
    public String getRcaMethod() { return rcaMethod; }
    public void setRcaMethod(String rcaMethod) { this.rcaMethod = rcaMethod; }
    public String getRcaSummary() { return rcaSummary; }
    public void setRcaSummary(String rcaSummary) { this.rcaSummary = rcaSummary; }
    public Boolean getSentinel() { return sentinel; }
    public void setSentinel(Boolean sentinel) { this.sentinel = sentinel; }
    public Boolean getReportedWithinSla() { return reportedWithinSla; }
    public void setReportedWithinSla(Boolean reportedWithinSla) { this.reportedWithinSla = reportedWithinSla; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
