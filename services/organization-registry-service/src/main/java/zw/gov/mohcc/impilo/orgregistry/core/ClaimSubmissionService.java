package zw.gov.mohcc.impilo.orgregistry.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Optional;
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

    private static final Logger log = LoggerFactory.getLogger(ClaimSubmissionService.class);

    private final OrgClaimSubmissionRepository claimRepository;
    private final OrganizationRepository organizationRepository;
    private final AuthorizedRepresentativeRepository representativeRepository;
    private final OrgRegistryOutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final WorkflowServiceClient workflowServiceClient;

    public ClaimSubmissionService(OrgClaimSubmissionRepository claimRepository,
                                  OrganizationRepository organizationRepository,
                                  AuthorizedRepresentativeRepository representativeRepository,
                                  OrgRegistryOutboxWriter outboxWriter,
                                  ObjectMapper objectMapper,
                                  WorkflowServiceClient workflowServiceClient) {
        this.claimRepository = claimRepository;
        this.organizationRepository = organizationRepository;
        this.representativeRepository = representativeRepository;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.workflowServiceClient = workflowServiceClient;
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

    /**
     * START ARC — escalates a SUBMITTED Channel-C claim to IATG adjudication.
     *
     * <p>Transitions the claim {@code SUBMITTED → UNDER_REVIEW} (durable first),
     * then makes a <b>best-effort, non-blocking</b> call into workflow-service to
     * start the adjudication workflow instance. The workflow-start is guaranteed
     * not to corrupt claim state:</p>
     * <ul>
     *   <li>start succeeds → the returned instance id is persisted on the claim
     *       ({@code workflowInstanceId}); adjudication is now in progress.</li>
     *   <li>start fails / feature disabled → the claim stays {@code UNDER_REVIEW}
     *       with a null instance id — an honest "adjudication pending/unavailable"
     *       state that can be retried, never a corrupted or fabricated one.</li>
     * </ul>
     *
     * @param initiatorRef actor initiating escalation (for workflow initiatorRef)
     */
    @Transactional
    public OrgClaimSubmissionEntity escalateToAdjudication(UUID tenantId, UUID claimId,
                                                           String initiatorRef) throws Exception {
        // Durable claim transition first — this commits regardless of the workflow call.
        OrgClaimSubmissionEntity claim = transition(tenantId, claimId, "UNDER_REVIEW", null);

        String correlationId = null;
        Optional<UUID> instanceId = workflowServiceClient.startAdjudication(
                tenantId, claim, initiatorRef, correlationId);
        if (instanceId.isPresent()) {
            claim.setWorkflowInstanceId(instanceId.get());
            claim = claimRepository.save(claim);
            log.info("Claim {} escalated to adjudication workflow instance {}",
                    claimId, instanceId.get());
        } else {
            log.warn("Claim {} escalated to UNDER_REVIEW but adjudication workflow did not start "
                    + "(pending/unavailable) — no workflow instance linked", claimId);
        }
        return claim;
    }

    /**
     * DECISION-FEEDBACK ARC — resolves the linked claim from a recorded IATG
     * adjudication decision (workforce-governance
     * {@code impilo.governance.adjudication.decision.recorded}).
     *
     * <p>Mapping: the decision's {@code workflowInstanceId} is the authoritative
     * join key to the escalated claim; {@code subjectRef} (which we set to the
     * claim id when starting the workflow) is a fallback. Decision outcome maps
     * to claim status: {@code APPROVED → ACCEPTED}, {@code DENIED → REJECTED}.
     * {@code CONDITIONAL} is not auto-resolved (left UNDER_REVIEW for follow-up).
     * The decision id becomes the authoritative {@code adjudicationRef}.</p>
     *
     * <p>Honest handling: a decision that cannot be mapped to a known claim is
     * logged and dropped (no crash, no fabricated claim). A duplicate decision
     * for an already-terminal claim is treated as idempotent and skipped.</p>
     *
     * @return the resolved claim, or empty when the decision does not resolve a
     *         known claim to a terminal state.
     */
    @Transactional
    public Optional<OrgClaimSubmissionEntity> resolveFromDecision(UUID tenantId, UUID workflowInstanceId,
                                                                  String subjectRef, String decision,
                                                                  String decisionId, String correlationId) {
        String target = mapDecisionToStatus(decision);
        if (target == null) {
            log.info("Adjudication decision '{}' (id={}) for workflow instance {} is not auto-resolvable "
                    + "— claim left UNDER_REVIEW", decision, decisionId, workflowInstanceId);
            return Optional.empty();
        }

        Optional<OrgClaimSubmissionEntity> match = locateClaim(tenantId, workflowInstanceId, subjectRef);
        if (match.isEmpty()) {
            log.warn("Adjudication decision (id={}, decision={}, workflowInstanceId={}, subjectRef={}) "
                    + "references an unknown claim for tenant {} — dropping (dead-letter)",
                    decisionId, decision, workflowInstanceId, subjectRef, tenantId);
            return Optional.empty();
        }
        OrgClaimSubmissionEntity claim = match.get();

        // Idempotency / duplicate delivery: claim already terminal.
        if ("ACCEPTED".equals(claim.getStatus()) || "REJECTED".equals(claim.getStatus())) {
            if (target.equals(claim.getStatus())) {
                log.info("Claim {} already resolved to {} — idempotent decision {} skipped",
                        claim.getId(), target, decisionId);
            } else {
                log.warn("Claim {} already terminal ({}) but decision {} maps to {} — refusing to override "
                        + "an append-only adjudication outcome", claim.getId(), claim.getStatus(), decisionId, target);
            }
            return Optional.of(claim);
        }

        try {
            // A decision implies the claim was under adjudication; bridge SUBMITTED->UNDER_REVIEW
            // when an APPROVED decision arrives (SUBMITTED->ACCEPTED is not a legal direct edge).
            if ("SUBMITTED".equals(claim.getStatus()) && "ACCEPTED".equals(target)) {
                transition(tenantId, claim.getId(), "UNDER_REVIEW", null);
            }
            OrgClaimSubmissionEntity resolved = transition(tenantId, claim.getId(), target, decisionId);
            log.info("Claim {} resolved to {} from adjudication decision {} (workflowInstanceId={})",
                    resolved.getId(), target, decisionId, workflowInstanceId);
            return Optional.of(resolved);
        } catch (Exception e) {
            log.warn("Failed to resolve claim {} from decision {} ({}: {}) — claim state unchanged",
                    claim.getId(), decisionId, e.getClass().getSimpleName(), e.getMessage());
            return Optional.of(claim);
        }
    }

    private Optional<OrgClaimSubmissionEntity> locateClaim(UUID tenantId, UUID workflowInstanceId, String subjectRef) {
        Optional<OrgClaimSubmissionEntity> byInstance = workflowInstanceId != null
                ? claimRepository.findByWorkflowInstanceId(workflowInstanceId)
                : Optional.empty();
        Optional<OrgClaimSubmissionEntity> claim = byInstance;
        if (claim.isEmpty() && subjectRef != null && !subjectRef.isBlank()) {
            try {
                claim = claimRepository.findById(UUID.fromString(subjectRef.trim()));
            } catch (IllegalArgumentException notAUuid) {
                claim = Optional.empty();
            }
        }
        return claim.filter(c -> tenantId != null && tenantId.equals(c.getTenantId()));
    }

    private static String mapDecisionToStatus(String decision) {
        if (decision == null) {
            return null;
        }
        return switch (decision.trim().toUpperCase()) {
            case "APPROVED", "DECIDED_APPROVED" -> "ACCEPTED";
            case "DENIED", "DECIDED_DENIED" -> "REJECTED";
            default -> null; // CONDITIONAL / unknown → not auto-resolved
        };
    }
}
