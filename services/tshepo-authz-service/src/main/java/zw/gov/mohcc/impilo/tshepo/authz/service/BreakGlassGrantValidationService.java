package zw.gov.mohcc.impilo.tshepo.authz.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.authz.dto.BreakGlassGrantValidationRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.BreakGlassGrantValidationResponse;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.BreakGlassRequestEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.VisibilityEscalationGrantEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.BreakGlassRequestRepository;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.VisibilityEscalationGrantRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves whether an actor currently holds a break-glass grant, for downstream services that
 * enforce emergency access in their own process.
 *
 * <h2>Why this exists</h2>
 * <p>tshepo-authz already held a real grant model and already validated it correctly — but only
 * for itself. {@code VisibilityEscalationService.resolveActiveGrant} had exactly one caller,
 * {@code PolicyEngine}, inside the PDP's own authorize path, and the service's public surface was
 * the workflow for <em>obtaining</em> a grant. Nothing exposed <em>validating</em> one. Guards that
 * sit outside the ext_authz path — {@code EmergencyAccessGuard} in madi, {@code ClinicalAccessGuard}
 * in pct — therefore had nothing to consult and fell back on comparing a client-supplied
 * purpose-of-use string. This service is the missing seam, nothing more.</p>
 *
 * <h2>Both grant models are consulted</h2>
 * <p>The estate has two, and they answer different questions:</p>
 * <ul>
 *   <li><b>Escalation grant</b> — token-scoped. The caller was handed an
 *       {@code x-escalation-grant-id}; the token must resolve to an ACTIVE, unexpired grant bound
 *       to this tenant <em>and</em> this actor.</li>
 *   <li><b>Break-glass request</b> — actor-scoped. The actor declared an emergency through
 *       {@code POST /v1/break-glass}, with a mandatory reason and a TTL, and that declaration has
 *       not yet lapsed.</li>
 * </ul>
 *
 * <p>An unresolvable token does <b>not</b> short-circuit to NO_GRANT: it falls through to the
 * actor-scoped check. Otherwise anyone able to attach a wrong or expired token to a request could
 * suppress a genuine break-glass declaration and turn a forged header into a denial-of-care.</p>
 *
 * <h2>What this deliberately does not do</h2>
 * <p>It grants nothing, mints nothing and mutates nothing — every path is a read. It answers one
 * closed question about a grant that already exists, and returns no roles, permissions, ceilings
 * or tokens. It is not an alternative to the PDP and cannot be used as one: it never sees a
 * resource, an action's required permission, or a policy rule.</p>
 */
@Service
public class BreakGlassGrantValidationService {

    private static final Logger log = LoggerFactory.getLogger(BreakGlassGrantValidationService.class);

    private final BreakGlassRequestRepository breakGlassRepository;
    private final VisibilityEscalationGrantRepository escalationGrantRepository;

    public BreakGlassGrantValidationService(BreakGlassRequestRepository breakGlassRepository,
                                            VisibilityEscalationGrantRepository escalationGrantRepository) {
        this.breakGlassRepository = breakGlassRepository;
        this.escalationGrantRepository = escalationGrantRepository;
    }

    /**
     * @return {@code VALID} when this actor holds an active grant in this tenant under either
     *         model, otherwise {@code NO_GRANT}. Never throws for an absent or malformed grant
     *         token — that is a NO_GRANT input, not a client error.
     */
    @Transactional(readOnly = true)
    public BreakGlassGrantValidationResponse validate(BreakGlassGrantValidationRequest request) {
        Instant now = Instant.now();

        Optional<VisibilityEscalationGrantEntity> escalation =
                resolveEscalationGrant(request.tenantId(), request.actorId(), request.grantToken(), now);
        if (escalation.isPresent()) {
            VisibilityEscalationGrantEntity g = escalation.get();
            log.info("Break-glass grant validated from escalation grant: tenant={}, actor={}, token={}, action={}",
                    request.tenantId(), request.actorId(), g.getGrantToken(), request.action());
            return BreakGlassGrantValidationResponse.valid(
                    BreakGlassGrantValidationResponse.SOURCE_ESCALATION_GRANT,
                    g.getGrantToken().toString(),
                    g.getExpiresAt(),
                    g.getStatus());
        }

        List<BreakGlassRequestEntity> active = breakGlassRepository
                .findActiveByTenantIdAndActorId(request.tenantId(), request.actorId(), now);
        if (!active.isEmpty()) {
            BreakGlassRequestEntity b = active.get(0); // repository orders by grantedAt DESC
            log.info("Break-glass grant validated from break-glass request: tenant={}, actor={}, id={}, action={}",
                    request.tenantId(), request.actorId(), b.getId(), request.action());
            return BreakGlassGrantValidationResponse.valid(
                    BreakGlassGrantValidationResponse.SOURCE_BREAK_GLASS_REQUEST,
                    b.getId().toString(),
                    b.getExpiresAt(),
                    b.getReviewStatus());
        }

        // Logged at WARN: a downstream guard is about to refuse an emergency action. That is the
        // correct outcome, and it is also exactly the event an on-call reviewer needs to see.
        log.warn("Break-glass grant NOT FOUND: tenant={}, actor={}, action={} — no active escalation grant "
                        + "and no active break-glass request; the calling guard will refuse.",
                request.tenantId(), request.actorId(), request.action());
        return BreakGlassGrantValidationResponse.noGrant();
    }

    private Optional<VisibilityEscalationGrantEntity> resolveEscalationGrant(
            UUID tenantId, String actorId, String grantToken, Instant now) {
        if (grantToken == null || grantToken.isBlank()) {
            return Optional.empty();
        }
        UUID token;
        try {
            token = UUID.fromString(grantToken.trim());
        } catch (IllegalArgumentException e) {
            // A malformed token is not a protocol error to raise at the caller; it simply resolves
            // to no grant, and the actor-scoped check still gets its turn.
            log.warn("Break-glass validation received a malformed grant token for actor={} in tenant={}",
                    actorId, tenantId);
            return Optional.empty();
        }
        return escalationGrantRepository.findActiveGrant(token, tenantId, actorId, now);
    }
}
