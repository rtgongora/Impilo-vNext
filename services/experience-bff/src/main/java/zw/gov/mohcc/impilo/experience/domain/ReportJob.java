package zw.gov.mohcc.impilo.experience.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "report_jobs")
public class ReportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "report_type", nullable = false)
    private String reportType;

    @Column(columnDefinition = "jsonb")
    private String parameters;

    @Column(nullable = false)
    private String status;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(name = "result_url")
    private String resultUrl;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "queued_at", nullable = false)
    private OffsetDateTime queuedAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    protected ReportJob() {}

    public ReportJob(String tenantId, String reportType, String parameters, String requestedBy) {
        this.tenantId = tenantId;
        this.reportType = reportType;
        this.parameters = parameters;
        this.requestedBy = requestedBy;
        this.status = "QUEUED";
        this.queuedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getReportType() { return reportType; }
    public String getParameters() { return parameters; }
    public String getStatus() { return status; }
    public String getRequestedBy() { return requestedBy; }
    public String getResultUrl() { return resultUrl; }
    public String getErrorMessage() { return errorMessage; }
    public OffsetDateTime getQueuedAt() { return queuedAt; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
}
