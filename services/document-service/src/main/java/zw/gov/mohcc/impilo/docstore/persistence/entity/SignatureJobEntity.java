package zw.gov.mohcc.impilo.docstore.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "signature_jobs", schema = "document_store")
public class SignatureJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, unique = true)
    private UUID jobId;

    @Column(name = "object_id", nullable = false)
    private UUID objectId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "provider_type", nullable = false)
    private String providerType;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "signer_id")
    private String signerId;

    @Column(name = "signature_ref")
    private String signatureRef;

    @Column(name = "verification_url")
    private String verificationUrl;

    @Column(name = "signed_at")
    private OffsetDateTime signedAt;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (jobId == null) {
            jobId = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (requestedAt == null) {
            requestedAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getJobId() { return jobId; }
    public void setJobId(UUID jobId) { this.jobId = jobId; }
    public UUID getObjectId() { return objectId; }
    public void setObjectId(UUID objectId) { this.objectId = objectId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getProviderType() { return providerType; }
    public void setProviderType(String providerType) { this.providerType = providerType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSignerId() { return signerId; }
    public void setSignerId(String signerId) { this.signerId = signerId; }
    public String getSignatureRef() { return signatureRef; }
    public void setSignatureRef(String signatureRef) { this.signatureRef = signatureRef; }
    public String getVerificationUrl() { return verificationUrl; }
    public void setVerificationUrl(String verificationUrl) { this.verificationUrl = verificationUrl; }
    public OffsetDateTime getSignedAt() { return signedAt; }
    public void setSignedAt(OffsetDateTime signedAt) { this.signedAt = signedAt; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(OffsetDateTime requestedAt) { this.requestedAt = requestedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
