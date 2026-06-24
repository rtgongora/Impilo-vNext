package zw.gov.mohcc.impilo.tshepo.authz.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.TrustAuthorityEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.TrustDocumentSignerCertEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.TrustIssuerSystemEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.TrustAuthorityRepository;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.TrustDocumentSignerCertRepository;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.TrustIssuerSystemRepository;
import zw.gov.mohcc.impilo.tshepo.authz.trust.TrustEnvironment;
import zw.gov.mohcc.impilo.tshepo.authz.trust.TrustReadiness;
import zw.gov.mohcc.impilo.tshepo.authz.trust.TrustStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tshepo Trust Authority registry. Records the trust authorities, authorised issuer systems
 * and document-signer certificate metadata Tshepo recognises, with an honest readiness
 * assessment per authority. Every mutating operation is gated by an admin role AND a recent
 * step-up (dual gate), and audited via the transactional outbox.
 */
@Service
public class TrustAuthorityService {

    private final TrustAuthorityRepository authorityRepository;
    private final TrustIssuerSystemRepository issuerRepository;
    private final TrustDocumentSignerCertRepository certRepository;
    private final StepUpService stepUpService;
    private final AuditPublisher auditPublisher;
    private final AuthzProperties properties;

    public TrustAuthorityService(TrustAuthorityRepository authorityRepository,
                                 TrustIssuerSystemRepository issuerRepository,
                                 TrustDocumentSignerCertRepository certRepository,
                                 StepUpService stepUpService,
                                 AuditPublisher auditPublisher,
                                 AuthzProperties properties) {
        this.authorityRepository = authorityRepository;
        this.issuerRepository = issuerRepository;
        this.certRepository = certRepository;
        this.stepUpService = stepUpService;
        this.auditPublisher = auditPublisher;
        this.properties = properties;
    }

    // ── Reads ──────────────────────────────────────────────────────────────

    public List<TrustAuthorityEntity> listAuthorities(UUID tenantId) {
        return authorityRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    public TrustAuthorityEntity getAuthority(UUID tenantId, UUID id) {
        return authorityRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Trust authority not found: " + id));
    }

    public List<TrustIssuerSystemEntity> listIssuers(UUID tenantId, UUID authorityId) {
        return issuerRepository.findByTrustAuthorityIdAndTenantIdOrderByCreatedAtDesc(authorityId, tenantId);
    }

    public List<TrustDocumentSignerCertEntity> listDocumentSigners(UUID tenantId, UUID authorityId) {
        return certRepository.findByTrustAuthorityIdAndTenantIdOrderByCreatedAtDesc(authorityId, tenantId);
    }

    // ── Mutations (admin + step-up gated, audited) ──────────────────────────

    @Transactional
    public TrustAuthorityEntity createAuthority(UUID tenantId, String actorId, List<String> roles,
                                                String name, String trustDomain, TrustEnvironment environment) {
        requireAdminAndStepUp(tenantId, actorId, roles);
        TrustAuthorityEntity entity = new TrustAuthorityEntity();
        entity.setTenantId(tenantId);
        entity.setName(name);
        entity.setTrustDomain(trustDomain);
        entity.setEnvironment(environment);
        entity.setStatus(TrustStatus.DRAFT);
        entity.setReadiness(TrustReadiness.NOT_STARTED);
        entity.setCreatedBy(actorId);
        entity = authorityRepository.save(entity);
        audit("TRUST_AUTHORITY_CREATED", entity.getId(), actorId,
                Map.of("name", name, "environment", environment.name()));
        return entity;
    }

    @Transactional
    public TrustAuthorityEntity transitionStatus(UUID tenantId, String actorId, List<String> roles,
                                                 UUID id, TrustStatus target) {
        requireAdminAndStepUp(tenantId, actorId, roles);
        TrustAuthorityEntity entity = getAuthority(tenantId, id);
        if (!entity.getStatus().canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Illegal status transition " + entity.getStatus() + " → " + target);
        }
        TrustStatus from = entity.getStatus();
        entity.setStatus(target);
        entity = authorityRepository.save(entity);
        audit("TRUST_AUTHORITY_STATUS_CHANGED", id, actorId,
                Map.of("from", from.name(), "to", target.name()));
        return entity;
    }

    @Transactional
    public TrustAuthorityEntity updateReadiness(UUID tenantId, String actorId, List<String> roles,
                                                UUID id, TrustReadiness readiness) {
        requireAdminAndStepUp(tenantId, actorId, roles);
        TrustAuthorityEntity entity = getAuthority(tenantId, id);
        TrustReadiness from = entity.getReadiness();
        entity.setReadiness(readiness);
        entity = authorityRepository.save(entity);
        audit("TRUST_AUTHORITY_READINESS_CHANGED", id, actorId,
                Map.of("from", from.name(), "to", readiness.name()));
        return entity;
    }

    @Transactional
    public TrustIssuerSystemEntity addIssuer(UUID tenantId, String actorId, List<String> roles,
                                             UUID authorityId, String name, String issuerUri) {
        requireAdminAndStepUp(tenantId, actorId, roles);
        getAuthority(tenantId, authorityId); // ensure it exists within the tenant
        TrustIssuerSystemEntity entity = new TrustIssuerSystemEntity();
        entity.setTenantId(tenantId);
        entity.setTrustAuthorityId(authorityId);
        entity.setName(name);
        entity.setIssuerUri(issuerUri);
        entity.setStatus(TrustStatus.DRAFT);
        entity = issuerRepository.save(entity);
        audit("TRUST_ISSUER_ADDED", entity.getId(), actorId,
                Map.of("authorityId", authorityId.toString(), "name", name));
        return entity;
    }

    @Transactional
    public TrustDocumentSignerCertEntity addDocumentSigner(UUID tenantId, String actorId, List<String> roles,
                                                           UUID authorityId, String kid, String subject,
                                                           Instant notBefore, Instant notAfter) {
        requireAdminAndStepUp(tenantId, actorId, roles);
        getAuthority(tenantId, authorityId);
        TrustDocumentSignerCertEntity entity = new TrustDocumentSignerCertEntity();
        entity.setTenantId(tenantId);
        entity.setTrustAuthorityId(authorityId);
        entity.setKid(kid);
        entity.setSubject(subject);
        entity.setNotBefore(notBefore);
        entity.setNotAfter(notAfter);
        entity.setStatus(TrustStatus.DRAFT);
        entity = certRepository.save(entity);
        audit("TRUST_DOCUMENT_SIGNER_ADDED", entity.getId(), actorId,
                Map.of("authorityId", authorityId.toString(), "kid", kid));
        return entity;
    }

    // ── Internal ────────────────────────────────────────────────────────────

    /**
     * Dual gate for every mutation: the caller must hold an admin role AND have a recent
     * step-up. Either failing is a hard {@link SecurityException} (fail closed).
     */
    private void requireAdminAndStepUp(UUID tenantId, String actorId, List<String> roles) {
        boolean admin = roles != null && roles.stream()
                .anyMatch(r -> properties.getTrustAuthorityAdminRoles().contains(r));
        if (!admin) {
            throw new SecurityException("Caller lacks a trust-authority admin role");
        }
        if (!stepUpService.hasRecentStepUp(tenantId, actorId)) {
            throw new SecurityException("Trust-authority changes require a recent step-up");
        }
    }

    private void audit(String eventType, UUID aggregateId, String actorId, Map<String, Object> extra) {
        Map<String, Object> payload = new java.util.HashMap<>(extra);
        payload.put("actorId", actorId);
        auditPublisher.queueGovernanceEvent(eventType, aggregateId.toString(), payload);
    }
}
