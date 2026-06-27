package zw.gov.mohcc.impilo.tshepo.authz.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import zw.gov.mohcc.impilo.tshepo.authz.trust.TrustEnvironment;
import zw.gov.mohcc.impilo.tshepo.authz.trust.TrustReadiness;
import zw.gov.mohcc.impilo.tshepo.authz.trust.TrustStatus;

import java.time.Instant;
import java.util.UUID;

/** A trust authority Tshepo recognises (e.g. a national/regional health trust domain). */
@Entity
@Table(name = "trust_authority", schema = "tshepo_authz")
public class TrustAuthorityEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "trust_domain", nullable = false)
    private String trustDomain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TrustEnvironment environment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TrustStatus status = TrustStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TrustReadiness readiness = TrustReadiness.NOT_STARTED;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTrustDomain() { return trustDomain; }
    public void setTrustDomain(String trustDomain) { this.trustDomain = trustDomain; }
    public TrustEnvironment getEnvironment() { return environment; }
    public void setEnvironment(TrustEnvironment environment) { this.environment = environment; }
    public TrustStatus getStatus() { return status; }
    public void setStatus(TrustStatus status) { this.status = status; }
    public TrustReadiness getReadiness() { return readiness; }
    public void setReadiness(TrustReadiness readiness) { this.readiness = readiness; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
