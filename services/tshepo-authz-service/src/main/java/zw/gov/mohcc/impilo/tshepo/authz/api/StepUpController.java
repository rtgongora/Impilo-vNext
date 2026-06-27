package zw.gov.mohcc.impilo.tshepo.authz.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.authz.dto.StepUpChallengeRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.StepUpChallengeResponse;
import zw.gov.mohcc.impilo.tshepo.authz.dto.StepUpVerifyRequest;
import zw.gov.mohcc.impilo.tshepo.authz.service.StepUpService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Step-up authentication challenge endpoints.
 *
 * <p>These endpoints are called by the client after receiving a STEP_UP_REQUIRED
 * response from the ext_authz check. The flow is:
 * <ol>
 *   <li>POST /v1/step-up/challenge — issue a new challenge</li>
 *   <li>Client presents the challenge to the user (MFA, biometric, supervisor)</li>
 *   <li>POST /v1/step-up/verify — submit the verification response</li>
 *   <li>Client retries the original request (PolicyEngine sees completed step-up)</li>
 * </ol>
 * </p>
 */
@RestController
@RequestMapping("/v1/step-up")
public class StepUpController {

    private static final Logger log = LoggerFactory.getLogger(StepUpController.class);

    private final StepUpService stepUpService;
    private final AuthzProperties properties;

    public StepUpController(StepUpService stepUpService, AuthzProperties properties) {
        this.stepUpService = stepUpService;
        this.properties = properties;
    }

    /**
     * Issue a new step-up challenge.
     */
    @PostMapping("/challenge")
    public ResponseEntity<StepUpChallengeResponse> issueChallenge(
            @Valid @RequestBody StepUpChallengeRequest request) {
        StepUpChallengeResponse response = stepUpService.issueChallenge(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Verify (complete) a step-up challenge.
     */
    @PostMapping("/verify")
    public ResponseEntity<StepUpChallengeResponse> verifyChallenge(
            @Valid @RequestBody StepUpVerifyRequest request) {
        try {
            StepUpChallengeResponse response = stepUpService.verifyChallenge(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Step-up verification failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (SecurityException e) {
            log.warn("Step-up verification security error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    /**
     * Record a supervisor's approval of a SUPERVISOR_APPROVAL challenge (dual control).
     *
     * <p>The supervisor's identity and authority are taken from the authenticated principal
     * (server-side trust context: {@code x-actor-id} + JWT realm roles) — never from the
     * request body. Fail-closed: a caller lacking a configured supervisor role is rejected
     * (403), and the service additionally enforces that a supervisor cannot approve their own
     * challenge. After approval, the original actor completes the challenge via {@code /verify}.
     * Closes the G058 residual (SUPERVISOR_APPROVAL had no controller endpoint).
     */
    @PostMapping("/{challengeId}/supervisor-approval")
    public ResponseEntity<Void> recordSupervisorApproval(
            @PathVariable UUID challengeId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("x-tenant-id") UUID tenantId,
            @RequestHeader("x-actor-id") String supervisorActorId) {
        boolean authorisedSupervisor = realmRoles(jwt).stream()
                .anyMatch(role -> properties.getStepUpSupervisorRoles().contains(role));
        try {
            stepUpService.recordSupervisorApproval(
                    challengeId, tenantId, supervisorActorId, authorisedSupervisor);
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            log.warn("Step-up supervisor approval forbidden: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            log.warn("Step-up supervisor approval rejected: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get the status of a step-up challenge.
     */
    @GetMapping("/status/{challengeId}")
    public ResponseEntity<StepUpChallengeResponse> getStatus(
            @PathVariable UUID challengeId,
            @RequestHeader("x-tenant-id") UUID tenantId) {
        try {
            StepUpChallengeResponse response = stepUpService.getStatus(challengeId, tenantId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Extract realm roles from the authenticated JWT (Keycloak {@code realm_access.roles}). */
    @SuppressWarnings("unchecked")
    private static List<String> realmRoles(Jwt jwt) {
        if (jwt == null) {
            return List.of();
        }
        Map<String, Object> realm = jwt.getClaimAsMap("realm_access");
        if (realm == null || !(realm.get("roles") instanceof List<?> list)) {
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
