package zw.gov.mohcc.impilo.costa.api.access;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.costa.api.dto.GrantWaiverRequest;
import zw.gov.mohcc.impilo.costa.api.dto.WaiverDecisionRequest;
import zw.gov.mohcc.impilo.costa.api.dto.WaiverResponse;
import zw.gov.mohcc.impilo.costa.service.WaiverService;
import zw.gov.mohcc.impilo.shared.auth.ActorTypeGuard;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Discretionary fee-waiver CRUD: grant → approve / reject → revoke.
 *
 * <p>Distinct from rules-driven exemptions. Errors mapped by
 * {@link ServiceAccessApiExceptionHandler}; approval/revocation emit a WAIVER_APPLIED value-event
 * (C8) for the audit trail and downstream rebilling.</p>
 *
 * <h2>Authorization, and the comment that stood in for it</h2>
 * <p>This javadoc previously read "Authz enforced upstream by Envoy ext_authz → TSHEPO". That
 * described a control which does not exist on this path. Envoy's configuration defines two clusters,
 * both experience-bff, and has no route to any domain service — so nothing was enforcing anything in
 * front of these four endpoints. Meanwhile every one of them already called
 * {@link TrustContextHolder}, purely to pull a correlation id out of it. The trust context was
 * present and unused: granting, approving, rejecting and revoking a discretionary fee waiver were
 * available to any authenticated principal, including a citizen — including the patient whose bill
 * it is.</p>
 *
 * <p>A comment asserting an upstream control is worse than no comment, because it answers the
 * question a reader would otherwise go and check. It is corrected rather than deleted, so the next
 * reader knows the claim was examined and found false rather than never made.</p>
 *
 * <p>Reads ({@code GET}) are left as they are — outside this change, and noted rather than altered
 * silently.</p>
 */
@RestController
@RequestMapping("/costa/v1/waivers")
public class WaiverController {

    /**
     * Deciding a discretionary waiver is back-office work: it writes off money owed.
     *
     * <p>{@link ActorTypeGuard} records why this cannot say "a facility finance officer
     * specifically" — actor type is not a role, and no role header reaches a service today. What it
     * does enforce is that the person whose bill is being waived cannot be the one waiving it.</p>
     */
    private static final Set<String> WAIVER_DECIDERS = ActorTypeGuard.BACK_OFFICE_WRITERS;

    private final WaiverService service;

    public WaiverController(WaiverService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<WaiverResponse>>> list(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "patient_cpid", required = false) String patientCpid) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(service.list(status, patientCpid), ctx.correlationId().toString()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WaiverResponse>> get(@PathVariable("id") UUID id) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(service.require(id), ctx.correlationId().toString()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WaiverResponse>> grant(@Valid @RequestBody GrantWaiverRequest body) {
        var ctx = TrustContextHolder.require();
        ActorTypeGuard.require(ctx, WAIVER_DECIDERS, "granting a fee waiver");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.grant(body), ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<WaiverResponse>> approve(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) WaiverDecisionRequest body) {
        var ctx = TrustContextHolder.require();
        ActorTypeGuard.require(ctx, WAIVER_DECIDERS, "approving a fee waiver");
        return ResponseEntity.ok(ApiResponse.ok(service.approve(id, body), ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<WaiverResponse>> reject(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) WaiverDecisionRequest body) {
        var ctx = TrustContextHolder.require();
        ActorTypeGuard.require(ctx, WAIVER_DECIDERS, "rejecting a fee waiver");
        return ResponseEntity.ok(ApiResponse.ok(service.reject(id, body), ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<ApiResponse<WaiverResponse>> revoke(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) WaiverDecisionRequest body) {
        var ctx = TrustContextHolder.require();
        ActorTypeGuard.require(ctx, WAIVER_DECIDERS, "revoking a fee waiver");
        return ResponseEntity.ok(ApiResponse.ok(service.revoke(id, body), ctx.correlationId().toString()));
    }
}
