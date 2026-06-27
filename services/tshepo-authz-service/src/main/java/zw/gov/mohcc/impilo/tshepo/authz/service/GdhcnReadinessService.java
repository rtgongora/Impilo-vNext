package zw.gov.mohcc.impilo.tshepo.authz.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.GdhcnReadinessAssessmentEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.GdhcnReadinessAssessmentRepository;
import zw.gov.mohcc.impilo.tshepo.authz.trust.GdhcnReadinessDomain;
import zw.gov.mohcc.impilo.tshepo.authz.trust.TrustReadiness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GDHCN readiness cockpit backend (Wave B B6). Presents the full fixed catalogue of readiness
 * domains for a tenant — merging code-defined domains with any saved assessment — so the
 * picture is always complete and honest (unassessed domains read as NOT_STARTED). Updates are
 * dual-gated (admin role + recent step-up) and audited, mirroring the Trust Authority registry.
 */
@Service
public class GdhcnReadinessService {

    private final GdhcnReadinessAssessmentRepository repository;
    private final StepUpService stepUpService;
    private final AuditPublisher auditPublisher;
    private final AuthzProperties properties;

    public GdhcnReadinessService(GdhcnReadinessAssessmentRepository repository,
                                 StepUpService stepUpService,
                                 AuditPublisher auditPublisher,
                                 AuthzProperties properties) {
        this.repository = repository;
        this.stepUpService = stepUpService;
        this.auditPublisher = auditPublisher;
        this.properties = properties;
    }

    /** A domain's readiness — the code catalogue entry merged with any saved assessment. */
    public record DomainReadiness(
            String domainKey, String title,
            TrustReadiness currentMaturity, TrustReadiness targetMaturity,
            String owner, String evidenceStatus, String remediationStatus,
            String linkedComponent, String updatedBy, Instant updatedAt) {}

    public List<DomainReadiness> listReadiness(UUID tenantId) {
        List<GdhcnReadinessAssessmentEntity> saved = repository.findByTenantId(tenantId);
        List<DomainReadiness> out = new ArrayList<>();
        for (GdhcnReadinessDomain domain : GdhcnReadinessDomain.values()) {
            GdhcnReadinessAssessmentEntity a = saved.stream()
                    .filter(e -> domain.name().equals(e.getDomainKey()))
                    .findFirst().orElse(null);
            if (a == null) {
                out.add(new DomainReadiness(domain.name(), domain.title(),
                        TrustReadiness.NOT_STARTED, TrustReadiness.PRODUCTION_NOT_READY,
                        null, null, null, domain.defaultLinkedComponent(), null, null));
            } else {
                out.add(toDto(domain, a));
            }
        }
        return out;
    }

    @Transactional
    public DomainReadiness updateDomain(UUID tenantId, String actorId, List<String> roles, String domainKey,
                                        TrustReadiness currentMaturity, TrustReadiness targetMaturity,
                                        String owner, String evidenceStatus, String remediationStatus,
                                        String linkedComponent) {
        requireAdminAndStepUp(tenantId, actorId, roles);
        GdhcnReadinessDomain domain = parseDomain(domainKey);

        GdhcnReadinessAssessmentEntity entity = repository
                .findByTenantIdAndDomainKey(tenantId, domain.name())
                .orElseGet(() -> {
                    GdhcnReadinessAssessmentEntity e = new GdhcnReadinessAssessmentEntity();
                    e.setTenantId(tenantId);
                    e.setDomainKey(domain.name());
                    return e;
                });
        if (currentMaturity != null) {
            entity.setCurrentMaturity(currentMaturity);
        }
        if (targetMaturity != null) {
            entity.setTargetMaturity(targetMaturity);
        }
        entity.setOwner(owner);
        entity.setEvidenceStatus(evidenceStatus);
        entity.setRemediationStatus(remediationStatus);
        entity.setLinkedComponent(linkedComponent != null ? linkedComponent : domain.defaultLinkedComponent());
        entity.setUpdatedBy(actorId);
        entity = repository.save(entity);

        auditPublisher.queueGovernanceEvent("GDHCN_READINESS_UPDATED", domain.name(),
                Map.of("actorId", actorId, "tenantId", tenantId.toString(),
                        "currentMaturity", entity.getCurrentMaturity().name()));
        return toDto(domain, entity);
    }

    private DomainReadiness toDto(GdhcnReadinessDomain domain, GdhcnReadinessAssessmentEntity a) {
        return new DomainReadiness(domain.name(), domain.title(),
                a.getCurrentMaturity(), a.getTargetMaturity(),
                a.getOwner(), a.getEvidenceStatus(), a.getRemediationStatus(),
                a.getLinkedComponent() != null ? a.getLinkedComponent() : domain.defaultLinkedComponent(),
                a.getUpdatedBy(), a.getUpdatedAt());
    }

    private GdhcnReadinessDomain parseDomain(String domainKey) {
        try {
            return GdhcnReadinessDomain.valueOf(domainKey);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Unknown GDHCN readiness domain: " + domainKey);
        }
    }

    private void requireAdminAndStepUp(UUID tenantId, String actorId, List<String> roles) {
        boolean admin = roles != null && roles.stream()
                .anyMatch(r -> properties.getTrustAuthorityAdminRoles().contains(r));
        if (!admin) {
            throw new SecurityException("Caller lacks a trust-authority admin role");
        }
        if (!stepUpService.hasRecentStepUp(tenantId, actorId)) {
            throw new SecurityException("GDHCN readiness changes require a recent step-up");
        }
    }
}
