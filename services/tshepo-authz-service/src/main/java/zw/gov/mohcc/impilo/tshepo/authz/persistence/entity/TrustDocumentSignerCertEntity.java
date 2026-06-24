package zw.gov.mohcc.impilo.tshepo.authz.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import zw.gov.mohcc.impilo.tshepo.authz.trust.TrustStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Document-signer certificate METADATA registered under a trust authority. Holds only the
 * public identifiers needed to recognise a signer ({@code kid}, subject, validity window) —
 * never private key material.
 */
@Entity
@Table(name = "trust_document_signer_cert", schema = "tshepo_authz")
public class TrustDocumentSignerCertEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "trust_authority_id", nullable = false)
    private UUID trustAuthorityId;

    @Column(nullable = false)
    private String kid;

    @Column(nullable = false, length = 512)
    private String subject;

    @Column(name = "not_before")
    private Instant notBefore;

    @Column(name = "not_after")
    private Instant notAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TrustStatus status = TrustStatus.DRAFT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getTrustAuthorityId() { return trustAuthorityId; }
    public void setTrustAuthorityId(UUID trustAuthorityId) { this.trustAuthorityId = trustAuthorityId; }
    public String getKid() { return kid; }
    public void setKid(String kid) { this.kid = kid; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public Instant getNotBefore() { return notBefore; }
    public void setNotBefore(Instant notBefore) { this.notBefore = notBefore; }
    public Instant getNotAfter() { return notAfter; }
    public void setNotAfter(Instant notAfter) { this.notAfter = notAfter; }
    public TrustStatus getStatus() { return status; }
    public void setStatus(TrustStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
