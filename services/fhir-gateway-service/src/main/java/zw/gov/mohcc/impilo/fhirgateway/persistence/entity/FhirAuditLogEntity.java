package zw.gov.mohcc.impilo.fhirgateway.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "fhir_audit_log", schema = "fhir_gateway")
public class FhirAuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "operation", nullable = false)
    private String operation;

    @Column(name = "source_ip")
    private String sourceIp;

    @Column(name = "actor_id")
    private String actorId;

    @Column(name = "outcome", nullable = false)
    private String outcome = "SUCCESS";

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "consent_outcome", length = 32)
    private String consentOutcome;

    /**
     * The HTTP status the destination actually returned.
     *
     * <p>Null when no call was made, or when the failure was transport-level — and that is the
     * distinction: null with {@code outcome=FORWARD_FAILED} means "never reached", 422 means
     * "reached and refused". The gateway knew this all along and discarded it, so a 403, a 409, a
     * 422 and a 500 were the same audit row.</p>
     */
    @Column(name = "downstream_status")
    private Integer downstreamStatus;

    @Column(name = "target_endpoint", length = 500)
    private String targetEndpoint;

    /** Pseudonymous subject. A consent denial is unanswerable without it. CPID only, never PII. */
    @Column(name = "subject_cpid", length = 64)
    private String subjectCpid;

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getSourceIp() { return sourceIp; }
    public void setSourceIp(String sourceIp) { this.sourceIp = sourceIp; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public UUID getCorrelationId() { return correlationId; }
    public void setCorrelationId(UUID correlationId) { this.correlationId = correlationId; }
    public String getConsentOutcome() { return consentOutcome; }
    public void setConsentOutcome(String consentOutcome) { this.consentOutcome = consentOutcome; }

    public Integer getDownstreamStatus() { return downstreamStatus; }
    public void setDownstreamStatus(Integer downstreamStatus) { this.downstreamStatus = downstreamStatus; }

    public String getTargetEndpoint() { return targetEndpoint; }
    public void setTargetEndpoint(String targetEndpoint) { this.targetEndpoint = targetEndpoint; }

    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String subjectCpid) { this.subjectCpid = subjectCpid; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
