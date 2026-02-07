package zw.gov.mohcc.impilo.tshepo.authz.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.tshepo.authz.dto.StepUpChallengeRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.StepUpChallengeResponse;
import zw.gov.mohcc.impilo.tshepo.authz.dto.StepUpVerifyRequest;
import zw.gov.mohcc.impilo.tshepo.authz.service.StepUpService;

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

    public StepUpController(StepUpService stepUpService) {
        this.stepUpService = stepUpService;
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
}
