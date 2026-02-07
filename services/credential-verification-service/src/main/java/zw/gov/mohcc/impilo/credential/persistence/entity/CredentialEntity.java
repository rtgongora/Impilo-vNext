package zw.gov.mohcc.impilo.credential.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "credentials", schema = "credential_verification")
public class CredentialEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "credential_id", nullable = false, unique = true, updatable = false)
    private UUID credentialId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_type", nullable = false, length = 50)
    private String subjectType;

    @Column(name = "subject_id", nullable = false, length = 255)
    private String subjectId;

    @Column(name = "subject_name", nullable = false, length = 500)
    private String subjectName;

    @Column(name = "credential_type", nullable = false, length = 100)
    private String credentialType;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "issued_by", nullable = false, length = 500)
    private String issuedBy;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "verification_token", nullable = false, unique = true, length = 64)
    private String verificationToken;

    @Column(name = "signature_hex", nullable = false, columnDefinition = "TEXT")
    private String signatureHex;

    @Column(name = "signed_payload", nullable = false, columnDefinition = "TEXT")
    private String signedPayload;

    @Column(name = "pdf_document_id")
    private UUID pdfDocumentId;

    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by", length = 255)
    private String revokedBy;

    @Column(name = "revocation_reason", length = 500)
    private String revocationReason;

    @Column(name = "superseded_by")
    private Long supersededBy;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (credentialId == null) {
            credentialId = UUID.randomUUID();
        }
        if (status == null) {
            status = "ACTIVE";
        }
        if (issuedAt == null) {
            issuedAt = Instant.now();
        }
        if (metadata == null) {
            metadata = "{}";
        }
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Convenience methods

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean isRevoked() {
        return "REVOKED".equals(status);
    }

    public boolean isExpired() {
        return validTo != null && LocalDate.now().isAfter(validTo);
    }

    public boolean isSuperseded() {
        return "SUPERSEDED".equals(status);
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getCredentialId() { return credentialId; }
    public void setCredentialId(UUID credentialId) { this.credentialId = credentialId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }

    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public String getCredentialType() { return credentialType; }
    public void setCredentialType(String credentialType) { this.credentialType = credentialType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getIssuedBy() { return issuedBy; }
    public void setIssuedBy(String issuedBy) { this.issuedBy = issuedBy; }

    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }

    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }

    public LocalDate getValidTo() { return validTo; }
    public void setValidTo(LocalDate validTo) { this.validTo = validTo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getVerificationToken() { return verificationToken; }
    public void setVerificationToken(String verificationToken) { this.verificationToken = verificationToken; }

    public String getSignatureHex() { return signatureHex; }
    public void setSignatureHex(String signatureHex) { this.signatureHex = signatureHex; }

    public String getSignedPayload() { return signedPayload; }
    public void setSignedPayload(String signedPayload) { this.signedPayload = signedPayload; }

    public UUID getPdfDocumentId() { return pdfDocumentId; }
    public void setPdfDocumentId(UUID pdfDocumentId) { this.pdfDocumentId = pdfDocumentId; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }

    public String getRevokedBy() { return revokedBy; }
    public void setRevokedBy(String revokedBy) { this.revokedBy = revokedBy; }

    public String getRevocationReason() { return revocationReason; }
    public void setRevocationReason(String revocationReason) { this.revocationReason = revocationReason; }

    public Long getSupersededBy() { return supersededBy; }
    public void setSupersededBy(Long supersededBy) { this.supersededBy = supersededBy; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}
