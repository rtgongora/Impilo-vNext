package zw.gov.mohcc.impilo.zibo.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import zw.gov.mohcc.impilo.zibo.domain.PolicyMode;
import zw.gov.mohcc.impilo.zibo.domain.ValidationOutcome;
import zw.gov.mohcc.impilo.zibo.domain.ValidationSeverity;

/**
 * Records every terminology validation event — successes included.
 *
 * <p>This table held zero rows until 2026-08-08, and could not have answered the question anyway:
 * it recorded a canonical URL and a free-text {@code details} blob, so "which code failed" was
 * recoverable only by parsing prose, and nothing recorded a success, so failures counted against no
 * denominator. The estate therefore had no measurement of how coded it actually is — every claim
 * about the coding gap rested on counting artifacts rather than counting what clinicians record.</p>
 *
 * <p>{@link #result} is the column that makes it a measurement rather than an error log. A
 * {@code RESOLVED} row is not noise; it is the denominator.</p>
 *
 * <p><b>No PHI.</b> The coding and its context are recorded — a clinical code, a FHIR element path,
 * the calling service. The resource body is not, and {@link #elementPath} is a path
 * ({@code Observation.code}), never a value.</p>
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

    /** The coding system URI as the caller supplied it, including one that resolves to no artifact. */
    @Column(name = "system")
    private String system;

    /** The code as supplied. A clinical code — never a patient identifier. */
    @Column(name = "code")
    private String code;

    @Column(name = "resource_type")
    private String resourceType;

    /** FHIR element path, e.g. {@code Observation.code}. The path, never the value. */
    @Column(name = "element_path")
    private String elementPath;

    /**
     * Which service asked. {@code service_name} has always been the hardcoded literal
     * "zibo-service", so it cannot distinguish oros from butano from the BFF.
     */
    @Column(name = "calling_service")
    private String callingService;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_mode")
    private PolicyMode policyMode;

    /** The measurement. {@code RESOLVED} rows are the denominator. */
    @Enumerated(EnumType.STRING)
    @Column(name = "result")
    private ValidationOutcome result;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "request_id")
    private String requestId;

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

    public String getSystem() { return system; }
    public void setSystem(String system) { this.system = system; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public String getElementPath() { return elementPath; }
    public void setElementPath(String elementPath) { this.elementPath = elementPath; }

    public String getCallingService() { return callingService; }
    public void setCallingService(String callingService) { this.callingService = callingService; }

    public PolicyMode getPolicyMode() { return policyMode; }
    public void setPolicyMode(PolicyMode policyMode) { this.policyMode = policyMode; }

    public ValidationOutcome getResult() { return result; }
    public void setResult(ValidationOutcome result) { this.result = result; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
