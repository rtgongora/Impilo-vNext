package zw.gov.mohcc.impilo.tshepo.authz.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import zw.gov.mohcc.impilo.tshepo.authz.trust.TrustReadiness;

import java.time.Instant;
import java.util.UUID;

/** Per-tenant assessment values for one GDHCN readiness domain. */
@Entity
@Table(name = "gdhcn_readiness_assessment", schema = "tshepo_authz")
public class GdhcnReadinessAssessmentEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "domain_key", nullable = false, length = 64)
    private String domainKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_maturity", nullable = false, length = 32)
    private TrustReadiness currentMaturity = TrustReadiness.NOT_STARTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_maturity", nullable = false, length = 32)
    private TrustReadiness targetMaturity = TrustReadiness.PRODUCTION_NOT_READY;

    @Column(name = "owner")
    private String owner;

    @Column(name = "evidence_status")
    private String evidenceStatus;

    @Column(name = "remediation_status")
    private String remediationStatus;

    @Column(name = "linked_component")
    private String linkedComponent;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getDomainKey() { return domainKey; }
    public void setDomainKey(String domainKey) { this.domainKey = domainKey; }
    public TrustReadiness getCurrentMaturity() { return currentMaturity; }
    public void setCurrentMaturity(TrustReadiness currentMaturity) { this.currentMaturity = currentMaturity; }
    public TrustReadiness getTargetMaturity() { return targetMaturity; }
    public void setTargetMaturity(TrustReadiness targetMaturity) { this.targetMaturity = targetMaturity; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public String getEvidenceStatus() { return evidenceStatus; }
    public void setEvidenceStatus(String evidenceStatus) { this.evidenceStatus = evidenceStatus; }
    public String getRemediationStatus() { return remediationStatus; }
    public void setRemediationStatus(String remediationStatus) { this.remediationStatus = remediationStatus; }
    public String getLinkedComponent() { return linkedComponent; }
    public void setLinkedComponent(String linkedComponent) { this.linkedComponent = linkedComponent; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
