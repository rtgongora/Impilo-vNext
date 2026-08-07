package zw.gov.mohcc.impilo.tshepo.authz.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.tshepo.authz.dto.CreateVisibilityEscalationRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.ReviewVisibilityEscalationRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.VisibilityEscalationGrantResponse;
import zw.gov.mohcc.impilo.tshepo.authz.dto.VisibilityEscalationRequestResponse;
import zw.gov.mohcc.impilo.tshepo.authz.service.VisibilityEscalationService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Workflow-gated visibility escalation (distinct from break-glass emergency access).
 */
@RestController
@RequestMapping("/v1/visibility-escalations")
public class VisibilityEscalationController {

    private final VisibilityEscalationService visibilityEscalationService;

    public VisibilityEscalationController(VisibilityEscalationService visibilityEscalationService) {
        this.visibilityEscalationService = visibilityEscalationService;
    }

    @PostMapping("/requests")
    public ResponseEntity<VisibilityEscalationRequestResponse> createRequest(
            @RequestHeader("x-tenant-id") UUID tenantId,
            @RequestHeader("x-actor-id") String actorId,
            @Valid @RequestBody CreateVisibilityEscalationRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(visibilityEscalationService.createRequest(tenantId, actorId, body));
    }

    @GetMapping("/requests/pending")
    public List<VisibilityEscalationRequestResponse> listPending(
            @RequestHeader("x-tenant-id") UUID tenantId,
            @RequestHeader("x-actor-id") String actorId) {
        return visibilityEscalationService.listPending(tenantId, actorId);
    }

    @PostMapping("/requests/{id}/review")
    public ResponseEntity<VisibilityEscalationGrantResponse> review(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("x-tenant-id") UUID tenantId,
            @PathVariable long id,
            @Valid @RequestBody ReviewVisibilityEscalationRequest body) {
        String reviewer = jwt != null ? jwt.getSubject() : "anonymous";
        List<String> roles = realmRoles(jwt);
        Optional<VisibilityEscalationGrantResponse> grant =
                visibilityEscalationService.reviewRequest(tenantId, id, reviewer, roles, body);
        return grant.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NO_CONTENT).body(null));
    }

    /** Request body for grant validation. The token travels in the body, never the query string. */
    public record ValidateGrantRequest(String grantId) {}

    /**
     * Outcome of a grant validation. {@code VALID} or {@code NO_GRANT} — never "unreachable", which
     * is not something this endpoint can report about itself. A caller that cannot reach this
     * service learns that from the call failing, and must treat it as a third, distinct case.
     */
    public record ValidateGrantResponse(String outcome, String visibilityCeiling, String workflowType) {}

    /**
     * Validate an escalation grant for a downstream service. Read-only.
     *
     * <p><b>This is not an authorization endpoint and must not become one.</b> It answers "is this
     * grant active for this actor in this tenant" and nothing else — no resource, no action, no
     * decision. A service needing an access decision calls the PDP. Break-glass guards need this
     * narrower question, and before this existed they had no way to ask it: the grant model was
     * reachable only from inside {@code PolicyEngine}, so guards compared a caller-supplied
     * purpose-of-use string instead.</p>
     *
     * <p>{@code POST} rather than {@code GET} despite being read-only, because the grant token is a
     * credential and query strings end up in access logs — the same reason
     * {@code ProviderBootstrapController#previewClaim} takes its token in the body.</p>
     *
     * <p><b>The actor is taken from the bearer token's subject, never from a header.</b> A caller can
     * therefore only ask "do <em>I</em> hold this grant" — it cannot ask about anybody else. Taking
     * {@code x-actor-id} here would have made this an oracle: an authenticated caller could probe
     * whether a given grant belongs to a given clinician. The identity that reaches this endpoint is
     * the clinician's own, forwarded by the calling service.</p>
     *
     * <p>Always 200 with an outcome; a missing, malformed, expired, revoked or wrong-actor grant is
     * {@code NO_GRANT}, indistinguishable from the caller's side so the endpoint cannot be used to
     * probe which grant tokens exist.</p>
     */
    @PostMapping("/grants/validate")
    public ResponseEntity<ValidateGrantResponse> validateGrant(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("x-tenant-id") UUID tenantId,
            @RequestBody ValidateGrantRequest body) {
        // The actor is the bearer's subject, never a header. A caller can only ask about themselves.
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            // Unauthenticated cannot reach here (anyRequest().authenticated()), but a token with no
            // subject would otherwise validate against a null actor and match nothing silently.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(visibilityEscalationService
                .validateGrant(tenantId, jwt.getSubject(), body == null ? null : body.grantId())
                .map(g -> new ValidateGrantResponse("VALID", g.visibilityCeiling(), g.workflowType()))
                .orElseGet(() -> new ValidateGrantResponse("NO_GRANT", null, null)));
    }

    @SuppressWarnings("unchecked")
    private static List<String> realmRoles(Jwt jwt) {
        if (jwt == null) {
            return List.of();
        }
        Map<String, Object> realm = jwt.getClaimAsMap("realm_access");
        if (realm == null || realm.get("roles") == null) {
            return List.of();
        }
        Object raw = realm.get("roles");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o != null) {
                out.add(o.toString());
            }
        }
        return out;
    }
}
