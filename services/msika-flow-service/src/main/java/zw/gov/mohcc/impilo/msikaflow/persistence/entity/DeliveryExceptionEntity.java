package zw.gov.mohcc.impilo.msikaflow.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.msikaflow.domain.ExceptionSeverity;
import zw.gov.mohcc.impilo.msikaflow.domain.ExceptionStatus;

import java.time.OffsetDateTime;

@Entity
@Table(name = "mf_delivery_exceptions")
public class DeliveryExceptionEntity {

    @Id
    @Column(name = "exception_id", nullable = false, length = 26)
    private String exceptionId;

    @Column(name = "plan_id", length = 26)
    private String planId;

    @Column(name = "leg_id", length = 26)
    private String legId;

    @Column(name = "exception_type", nullable = false, length = 60)
    private String exceptionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private ExceptionSeverity severity = ExceptionSeverity.MAJOR;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExceptionStatus status = ExceptionStatus.OPEN;

    @Column(name = "raised_at", nullable = false)
    private OffsetDateTime raisedAt;

    @Column(name = "resolution_summary", columnDefinition = "TEXT")
    private String resolutionSummary;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadataJson = "{}";

    @PrePersist
    protected void onCreate() {
        raisedAt = OffsetDateTime.now();
        if (metadataJson == null) metadataJson = "{}";
    }

    @PreUpdate
    protected void onUpdate() {
        // no-op
    }

    public String getExceptionId() { return exceptionId; }
    public void setExceptionId(String exceptionId) { this.exceptionId = exceptionId; }
    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
    public String getLegId() { return legId; }
    public void setLegId(String legId) { this.legId = legId; }
    public String getExceptionType() { return exceptionType; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }
    public ExceptionSeverity getSeverity() { return severity; }
    public void setSeverity(ExceptionSeverity severity) { this.severity = severity; }
    public ExceptionStatus getStatus() { return status; }
    public void setStatus(ExceptionStatus status) { this.status = status; }
    public OffsetDateTime getRaisedAt() { return raisedAt; }
    public String getResolutionSummary() { return resolutionSummary; }
    public void setResolutionSummary(String resolutionSummary) { this.resolutionSummary = resolutionSummary; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}

