package zw.gov.mohcc.impilo.patientsafety.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An E2B(R3)-ALIGNED export-readiness package for a case. Honesty contract: this is a capture/export
 * package, NOT a certified/validated E2B(R3) transmission. {@code adapterEnabled} defaults false; while
 * it is false the case is "export pending — adapter not configured", never "submitted".
 */
@Entity
@Table(name = "ps_e2b_export_attempt")
public class E2bExportAttemptEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "case_id", nullable = false)
    private UUID caseId;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "package_format", nullable = false, length = 32)
    private String packageFormat = "E2B_R3_ALIGNED";
    @Column(name = "status", nullable = false, length = 24)
    private String status = "EXPORT_READY";    // BUILT|EXPORT_READY|DISPATCH_PENDING|DISPATCH_FAILED|NOT_CONFIGURED
    @Column(name = "adapter_key", nullable = false, length = 64)
    private String adapterKey = "vigiflow";
    @Column(name = "adapter_enabled", nullable = false)
    private boolean adapterEnabled = false;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_summary", columnDefinition = "jsonb")
    private String payloadSummary;
    @Column(name = "message", length = 512)
    private String message;
    @Column(name = "created_by", length = 255)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected E2bExportAttemptEntity() {}

    public E2bExportAttemptEntity(UUID caseId, UUID tenantId) {
        this.id = UUID.randomUUID();
        this.caseId = caseId;
        this.tenantId = tenantId;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getCaseId() { return caseId; }
    public UUID getTenantId() { return tenantId; }
    public String getPackageFormat() { return packageFormat; }
    public void setPackageFormat(String v) { this.packageFormat = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getAdapterKey() { return adapterKey; }
    public void setAdapterKey(String v) { this.adapterKey = v; }
    public boolean isAdapterEnabled() { return adapterEnabled; }
    public void setAdapterEnabled(boolean v) { this.adapterEnabled = v; }
    public String getPayloadSummary() { return payloadSummary; }
    public void setPayloadSummary(String v) { this.payloadSummary = v; }
    public String getMessage() { return message; }
    public void setMessage(String v) { this.message = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { this.createdBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
