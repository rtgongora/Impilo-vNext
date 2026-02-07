package zw.gov.mohcc.impilo.zibo.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import zw.gov.mohcc.impilo.zibo.domain.ValidationSeverity;

/**
 * Records individual terminology validation events for audit and analytics.
 * Used to track validation failures, warnings, and conformance trends.
 */
@Entity
@Table(name = "zibo_validation_logs")
public class ValidationLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "log_id", nullable = false)
    private UUID logId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "service_name")
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity")
    private ValidationSeverity severity;

    @Column(name = "issue_code")
    private String issueCode;

    @Column(name = "canonical_url")
    private String canonicalUrl;

    @Column(name = "version")
    private String version;

    @Column(name = "details")
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // getId/setId aliases

    public UUID getId() { return logId; }
    public void setId(UUID id) { this.logId = id; }

    // Getters and setters

    public UUID getLogId() { return logId; }
    public void setLogId(UUID logId) { this.logId = logId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public ValidationSeverity getSeverity() { return severity; }
    public void setSeverity(ValidationSeverity severity) { this.severity = severity; }

    public String getIssueCode() { return issueCode; }
    public void setIssueCode(String issueCode) { this.issueCode = issueCode; }

    public String getCanonicalUrl() { return canonicalUrl; }
    public void setCanonicalUrl(String canonicalUrl) { this.canonicalUrl = canonicalUrl; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
