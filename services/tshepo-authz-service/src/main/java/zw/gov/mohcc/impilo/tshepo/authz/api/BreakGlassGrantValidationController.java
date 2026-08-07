package zw.gov.mohcc.impilo.tshepo.authz.api;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.tshepo.authz.dto.BreakGlassGrantValidationRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.BreakGlassGrantValidationResponse;
import zw.gov.mohcc.impilo.tshepo.authz.service.BreakGlassGrantValidationService;

/**
 * Grant validation for services that enforce emergency access outside the ext_authz path.
 *
 * <h2>The gap this closes</h2>
 * <p>{@code /v1/break-glass} lets a clinician <em>obtain</em> a grant. Until this endpoint there
 * was no way to <em>check</em> one from anywhere except inside the PDP's own authorize path, so
 * madi's {@code EmergencyAccessGuard} and pct's {@code ClinicalAccessGuard} — neither of which
 * sits behind Envoy — unlocked emergency clinical access on a client-supplied purpose-of-use
 * string. Those guards now call this.</p>
 *
 * <h2>Why this is not a second authorization endpoint</h2>
 * <p>An endpoint that answers "may this caller do X" is a PDP, and a second PDP is a second policy
 * surface to keep in agreement with the first. This one is shaped so it cannot become that:</p>
 *
 * <ul>
 *   <li><b>It takes no resource and no permission.</b> The request names a tenant and optionally a
 *       grant token; the actor is the bearer's own subject and cannot be named by the caller.
 *       There is nowhere to put "and may they release blood", so no caller can come to depend on
 *       this endpoint for that answer.</li>
 *   <li><b>It returns no entitlements.</b> The response is a verdict plus provenance for the audit
 *       trail — no roles, permissions, visibility ceilings, capabilities or tokens. There is
 *       nothing in the body a caller could cache and treat as authority.</li>
 *   <li><b>It is read-only.</b> Every path down to the repositories is a read. It cannot mint,
 *       extend, or approve a grant; a caller that wants one still goes through the request-and-
 *       review workflow, which is where the reason text and the supervisor live.</li>
 *   <li><b>It is not an authentication path.</b> It requires a JWT like every other management
 *       endpoint on this service ({@code anyRequest().authenticated()}), and it issues no
 *       credential. It cannot be used to obtain access to tshepo-authz, only to ask a question
 *       once you already have it.</li>
 * </ul>
 *
 * <p>A VALID answer means "this actor holds a live break-glass grant". It does not mean "allow the
 * action" — the calling guard still owns that decision, and still owns the emergency clinical
 * judgement around it.</p>
 */
@RestController
@RequestMapping("/v1/break-glass/validate")
public class BreakGlassGrantValidationController {

    private final BreakGlassGrantValidationService validationService;

    public BreakGlassGrantValidationController(BreakGlassGrantValidationService validationService) {
        this.validationService = validationService;
    }

    /**
     * POST /v1/break-glass/validate — does this actor hold an active break-glass grant?
     *
     * <p>Always 200 with a verdict when the question could be answered. It never 404s on "no
     * grant": a 404 is indistinguishable at the client from a wrong URL or a missing route, and a
     * client that cannot tell "refused" from "could not ask" is the exact defect this endpoint
     * exists to remove. Absence of a grant is an answer, and it is returned as one.</p>
     */
    @PostMapping
    public ResponseEntity<BreakGlassGrantValidationResponse> validate(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BreakGlassGrantValidationRequest request) {

        // THE ACTOR IS THE BEARER'S SUBJECT, NEVER THE BODY.
        //
        // This previously took actorId from the request, which made the endpoint an ORACLE: any
        // authenticated caller could ask whether a named clinician holds a live break-glass grant.
        // A caller may only ask about themselves.
        //
        // The paired half of this fix lives in HttpBreakGlassGrantClient, which must send the
        // CALLER'S token and never this workload's own. If a service-account token were sent, the
        // subject here would be the service, the grant lookup would find nothing, and every
        // emergency would be refused — the exact failure the PO ruling exists to prevent, arriving
        // silently the day s2s tokens are enabled. The two halves only work together.
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            // Unauthenticated cannot reach here (anyRequest().authenticated()), but a token with no
            // subject would otherwise validate against a null actor and match nothing silently —
            // which reads as "no grant" and refuses.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(validationService.validate(jwt.getSubject(), request));
    }
}
