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

/** An issuer system authorised under a trust authority (e.g. a national issuing service). */
@Entity
@Table(name = "trust_issuer_system", schema = "tshepo_authz")
public class TrustIssuerSystemEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "trust_authority_id", nullable = false)
    private UUID trustAuthorityId;

    @Column(nullable = false)
    private String name;

    @Column(name = "issuer_uri", nullable = false, length = 512)
    private String issuerUri;

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
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIssuerUri() { return issuerUri; }
    public void setIssuerUri(String issuerUri) { this.issuerUri = issuerUri; }
    public TrustStatus getStatus() { return status; }
    public void setStatus(TrustStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
