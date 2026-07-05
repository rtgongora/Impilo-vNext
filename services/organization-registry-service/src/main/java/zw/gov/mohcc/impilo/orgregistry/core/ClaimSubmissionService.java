package zw.gov.mohcc.impilo.orgregistry.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.orgregistry.api.OrgRegistryDtos;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.AuthorizedRepresentativeEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrgClaimSubmissionEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.VerificationStatus;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.AuthorizedRepresentativeRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.OrgClaimSubmissionRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.OrganizationRepository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Channel-C delegated onboarding claims.
 *
 * <p>State machine: {@code SUBMITTED → UNDER_REVIEW → ACCEPTED | REJECTED}
 * (a claim may also be rejected directly from SUBMITTED). ACCEPTED and
 * REJECTED are terminal.
 */
@Service
public class ClaimSubmissionService {

    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "SUBMITTED", Set.of("UNDER_REVIEW", "REJECTED"),
            "UNDER_REVIEW", Set.of("ACCEPTED", "REJECTED"),
            "ACCEPTED", Set.of(),
            "REJECTED", Set.of());

    private final OrgClaimSubmissionRepository claimRepository;
    private final OrganizationRepository organizationRepository;
    private final AuthorizedRepresentativeRepository representativeRepository;
    private final OrgRegistryOutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    public ClaimSubmissionService(OrgClaimSubmissionRepository claimRepository,
                                  OrganizationRepository organizationRepository,
                                  AuthorizedRepresentativeRepository representativeRepository,
                                  OrgRegistryOutboxWriter outboxWriter,
                                  ObjectMapper objectMapper) {
        this.claimRepository = claimRepository;
        this.organizationRepository = organizationRepository;
        this.representativeRepository = representativeRepository;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OrgClaimSubmissionEntity submit(UUID tenantId, UUID organizationId,
                                           OrgRegistryDtos.CreateClaimRequest request) throws Exception {
        organizationRepository.findByTenantIdAndId(tenantId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("organization not found: " + organizationId));
        if (request.submittedByRepId() == null) {
            throw new IllegalArgumentException("submittedByRepId is required");
        }
        if (request.subjectHealthId() == null || request.subjectHealthId().isBlank()) {
            throw new IllegalArgumentException("subjectHealthId is required");
        }
        if (request.claimedRole() == null || request.claimedRole().isBlank()) {
            throw new IllegalArgumentException("claimedRole is required");
        }
        AuthorizedRepresentativeEntity rep = representativeRepository
                .findByOrganizationIdAndId(organizationId, request.submittedByRepId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "representative does not belong to organization: " + request.submittedByRepId()));
        if (rep.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new IllegalStateException(
                    "claims may only be submitted by a VERIFIED authorized representative");
        }
        OrgClaimSubmissionEntity claim = new OrgClaimSubmissionEntity();
        claim.setTenantId(tenantId);
        claim.setOrganizationId(organizationId);
        claim.setSubmittedByRepId(rep.getId());
        claim.setSubjectHealthId(request.subjectHealthId().trim());
        claim.setClaimedRole(request.claimedRole().trim());
        claim.setTrustBasis(request.trustBasis() == null || request.trustBasis().isBlank()
                ? "ORG_DELEGATED" : request.trustBasis().trim().toUpperCase());
        if (request.evidence() != null) {
            claim.setEvidence(objectMapper.writeValueAsString(request.evidence()));
        }
        claim.setStatus("SUBMITTED");
        OrgClaimSubmissionEntity saved = claimRepository.save(claim);
        outboxWriter.publish(tenantId, "ORG_CLAIM", saved.getId().toString(),
                "claim_submission", "submitted",
                "org-registry:claim:submitted:" + saved.getId(),
                Map.of(
                        "claimId", saved.getId().toString(),
                        "organizationId", organizationId.toString(),
                        "subjectHealthId", saved.getSubjectHealthId(),
                        "claimedRole", saved.getClaimedRole()));
        return saved;
    }

    public OrgClaimSubmissionEntity get(UUID tenantId, UUID claimId) {
        OrgClaimSubmissionEntity claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("claim not found: " + claimId));
        if (!tenantId.equals(claim.getTenantId())) {
            throw new IllegalArgumentException("claim not found: " + claimId);
        }
        return claim;
    }

    public List<OrgClaimSubmissionEntity> listForOrganization(UUID tenantId, UUID organizationId) {
        organizationRepository.findByTenantIdAndId(tenantId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("organization not found: " + organizationId));
        return claimRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId);
    }

    @Transactional
    public OrgClaimSubmissionEntity transition(UUID tenantId, UUID claimId, String newStatus,
                                               String adjudicationRef) throws Exception {
        if (newStatus == null || newStatus.isBlank()) {
            throw new IllegalArgumentException("status is required");
        }
        String target = newStatus.trim().toUpperCase();
        if (!ALLOWED_TRANSITIONS.containsKey(target)) {
            throw new IllegalArgumentException("unknown claim status: " + target);
        }
        OrgClaimSubmissionEntity claim = get(tenantId, claimId);
        Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(claim.getStatus(), Set.of());
        if (!allowed.contains(target)) {
            throw new IllegalStateException(
                    "illegal claim transition " + claim.getStatus() + " -> " + target);
        }
        claim.setStatus(target);
        if (adjudicationRef != null && !adjudicationRef.isBlank()) {
            claim.setAdjudicationRef(adjudicationRef.trim());
        }
        OrgClaimSubmissionEntity saved = claimRepository.save(claim);
        outboxWriter.publish(tenantId, "ORG_CLAIM", saved.getId().toString(),
                "claim_submission", target.toLowerCase(),
                "org-registry:claim:" + target.toLowerCase() + ":" + saved.getId(),
                Map.of(
                        "claimId", saved.getId().toString(),
                        "organizationId", saved.getOrganizationId().toString(),
                        "status", saved.getStatus()));
        return saved;
    }
}
