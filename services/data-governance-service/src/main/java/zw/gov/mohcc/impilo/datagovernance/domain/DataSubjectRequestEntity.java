package zw.gov.mohcc.impilo.datagovernance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A data-subject rights request (GDPR Art. 15–18) — currently account deletion /
 * right-to-erasure. The lifecycle source of truth; downstream consumers of the
 * emitted outbox event perform the actual erasure.
 */
@Entity
@Table(name = "dgv_data_subject_request")
public class DataSubjectRequestEntity {

    @Id
    @Column(name = "request_id", nullable = false)
    private UUID requestId = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_id", nullable = false, length = 255)
    private String subjectId;

    @Column(name = "request_type", nullable = false, length = 32)
    private String requestType = "ACCOUNT_DELETION";

    @Column(name = "status", nullable = false, length = 32)
    private String status = "PENDING";

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "correlation_id", length = 255)
    private String correlationId;

    protected DataSubjectRequestEntity() {}

    public DataSubjectRequestEntity(UUID tenantId, String subjectId, String requestType,
                                    String reason, String correlationId) {
        this.tenantId = tenantId;
        this.subjectId = subjectId;
        this.requestType = requestType != null ? requestType : "ACCOUNT_DELETION";
        this.reason = reason;
        this.correlationId = correlationId;
    }

    public UUID getRequestId() { return requestId; }
    public UUID getTenantId() { return tenantId; }
    public String getSubjectId() { return subjectId; }
    public String getRequestType() { return requestType; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getCancelledAt() { return cancelledAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public String getCorrelationId() { return correlationId; }

    public void setStatus(String status) { this.status = status; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public void setCancelledAt(OffsetDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
}
