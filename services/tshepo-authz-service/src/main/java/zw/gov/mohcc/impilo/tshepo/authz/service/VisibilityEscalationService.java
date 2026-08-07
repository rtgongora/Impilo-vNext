package zw.gov.mohcc.impilo.tshepo.authz.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.authz.dto.AuthzInternalRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.CreateVisibilityEscalationRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.EscalationGrantView;
import zw.gov.mohcc.impilo.tshepo.authz.dto.ReviewVisibilityEscalationRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.VisibilityEscalationGrantResponse;
import zw.gov.mohcc.impilo.tshepo.authz.dto.VisibilityEscalationRequestResponse;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.VisibilityEscalationGrantEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.VisibilityEscalationRequestEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.VisibilityEscalationGrantRepository;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.VisibilityEscalationRequestRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class VisibilityEscalationService {

    private static final Logger log = LoggerFactory.getLogger(VisibilityEscalationService.class);

    private final VisibilityEscalationRequestRepository requestRepository;
    private final VisibilityEscalationGrantRepository grantRepository;
    private final AuthzProperties properties;
    private final AuditPublisher auditPublisher;

    public VisibilityEscalationService(VisibilityEscalationRequestRepository requestRepository,
                                     VisibilityEscalationGrantRepository grantRepository,
                                     AuthzProperties properties,
                                     AuditPublisher auditPublisher) {
        this.requestRepository = requestRepository;
        this.grantRepository = grantRepository;
        this.properties = properties;
        this.auditPublisher = auditPublisher;
    }

    @Transactional
    public VisibilityEscalationRequestResponse createRequest(UUID tenantId, String actorId,
                                                            CreateVisibilityEscalationRequest body) {
        VisibilityEscalationRequestEntity e = new VisibilityEscalationRequestEntity();
        e.setTenantId(tenantId);
        e.setActorId(actorId);
        e.setWorkflowType(body.workflowType());
        e.setJustification(body.justification());
        e.setRequestedVisibilityCeiling(body.requestedVisibilityCeiling());
        e.setStatus("PENDING");
        e = requestRepository.save(e);
        auditPublisher.queueGovernanceEvent("ESCALATION_REQUESTED", e.getId().toString(), Map.of(
                "tenantId", tenantId.toString(),
                "actorId", actorId,
                "workflowType", body.workflowType(),
                "requestedVisibilityCeiling", body.requestedVisibilityCeiling()
        ));
        return toResponse(e);
    }

    @Transactional
    public Optional<VisibilityEscalationGrantResponse> reviewRequest(UUID tenantId, long requestId,
                                                                     String reviewerActorId,
                                                                     List<String> reviewerRoles,
                                                                     ReviewVisibilityEscalationRequest body) {
        VisibilityEscalationRequestEntity req = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown escalation request"));
        if (!tenantId.equals(req.getTenantId())) {
            throw new IllegalArgumentException("Tenant mismatch");
        }
        if (!"PENDING".equalsIgnoreCase(req.getStatus())) {
            throw new IllegalStateException("Request is not pending");
        }
        boolean approver = reviewerRoles.stream().anyMatch(
                r -> properties.getVisibilityEscalationApproverRoles().contains(r));
        if (!approver) {
            throw new SecurityException("Caller lacks visibility escalation approver role");
        }
        req.setReviewedBy(reviewerActorId);
        req.setReviewNotes(body.reviewNotes());
        if (Boolean.FALSE.equals(body.approved())) {
            req.setStatus("REJECTED");
            requestRepository.save(req);
            auditPublisher.queueGovernanceEvent("ESCALATION_DENIED", req.getId().toString(), Map.of(
                    "tenantId", tenantId.toString(),
                    "requestId", requestId,
                    "reviewer", reviewerActorId
            ));
            return Optional.empty();
        }
        req.setStatus("APPROVED");
        requestRepository.save(req);

        VisibilityEscalationGrantEntity g = new VisibilityEscalationGrantEntity();
        g.setRequestId(req.getId());
        g.setTenantId(tenantId);
        g.setActorId(req.getActorId());
        g.setGrantToken(UUID.randomUUID());
        g.setWorkflowType(req.getWorkflowType());
        g.setVisibilityCeiling(req.getRequestedVisibilityCeiling());
        g.setStatus("ACTIVE");
        g.setExpiresAt(Instant.now().plusSeconds(properties.getVisibilityEscalationGrantTtlHours() * 3600L));
        g = grantRepository.save(g);

        auditPublisher.queueGovernanceEvent("ESCALATION_APPROVED", g.getGrantToken().toString(), Map.of(
                "tenantId", tenantId.toString(),
                "actorId", req.getActorId(),
                "workflowType", req.getWorkflowType(),
                "visibilityCeiling", g.getVisibilityCeiling(),
                "grantToken", g.getGrantToken().toString()
        ));

        return Optional.of(new VisibilityEscalationGrantResponse(
                g.getGrantToken(), g.getVisibilityCeiling(), g.getWorkflowType(), g.getExpiresAt(), req.getId()));
    }

    public List<VisibilityEscalationRequestResponse> listPending(UUID tenantId, String actorId) {
        return requestRepository
                .findByTenantIdAndActorIdAndStatusOrderByCreatedAtDesc(tenantId, actorId, "PENDING")
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Validate an escalation grant on behalf of a downstream service.
     *
     * <p>Purpose-built and read-only. It answers exactly one question — "is this grant token active,
     * for this actor, in this tenant, right now" — and deliberately nothing else. It is <b>not</b> an
     * authorization endpoint: it does not know what the caller intends to do and must never grow a
     * resource, an action or a decision. A service that needs an access decision calls the PDP.</p>
     *
     * <p>It exists because the grant model had no seam. {@link #resolveActiveGrant} had exactly one
     * caller — {@code PolicyEngine} — inside the PDP's own authorize path, and the API surface
     * offered only the workflow for <em>obtaining</em> a grant. Break-glass guards in domain services
     * therefore had nothing to consult, and fell back to comparing a purpose-of-use string that the
     * caller supplies.</p>
     *
     * <p>Returns empty for <b>every</b> reason a grant does not hold: absent, malformed, expired,
     * revoked, or bound to a different actor. The caller must not be able to tell those apart — the
     * distinction that matters to a caller is "no grant" versus "could not ask", and the second is
     * not something this method can express. It is signalled by the call failing.</p>
     */
    @Transactional(readOnly = true)
    public Optional<EscalationGrantView> validateGrant(UUID tenantId, String actorId, String grantId) {
        if (tenantId == null || actorId == null || actorId.isBlank()
                || grantId == null || grantId.isBlank()) {
            return Optional.empty();
        }
        UUID token;
        try {
            token = UUID.fromString(grantId.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return grantRepository.findActiveGrant(token, tenantId, actorId.trim(), Instant.now())
                .map(g -> new EscalationGrantView(g.getGrantToken(), g.getVisibilityCeiling(), g.getWorkflowType()));
    }

    public Optional<EscalationGrantView> resolveActiveGrant(AuthzInternalRequest request) {
        if (request.escalationGrantId() == null || request.escalationGrantId().isBlank()) {
            return Optional.empty();
        }
        UUID token;
        try {
            token = UUID.fromString(request.escalationGrantId().trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return grantRepository.findActiveGrant(token, request.tenantId(), request.actorId(), Instant.now())
                .map(g -> new EscalationGrantView(g.getGrantToken(), g.getVisibilityCeiling(), g.getWorkflowType()));
    }

    private VisibilityEscalationRequestResponse toResponse(VisibilityEscalationRequestEntity e) {
        return new VisibilityEscalationRequestResponse(
                e.getId(),
                e.getTenantId(),
                e.getActorId(),
                e.getWorkflowType(),
                e.getJustification(),
                e.getRequestedVisibilityCeiling(),
                e.getStatus(),
                e.getReviewedBy(),
                e.getReviewNotes(),
                e.getCreatedAt()
        );
    }
}
