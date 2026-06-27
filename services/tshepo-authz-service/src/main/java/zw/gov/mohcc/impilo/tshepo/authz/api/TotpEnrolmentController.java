package zw.gov.mohcc.impilo.tshepo.authz.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.tshepo.authz.stepup.TotpEnrolmentService;

import java.util.Map;
import java.util.UUID;

/**
 * Authenticator-app TOTP enrolment endpoints (step-up). The enrolling actor's identity is taken
 * from the server-side trust context ({@code x-tenant-id} / {@code x-actor-id}) — an actor enrols
 * only their own secret; the body never carries the actor or the secret.
 *
 * <p>Registered only when {@code tshepo.authz.totp.enrolment-enabled=true}.</p>
 */
@RestController
@RequestMapping("/v1/step-up/totp")
@ConditionalOnProperty(name = "tshepo.authz.totp.enrolment-enabled", havingValue = "true")
public class TotpEnrolmentController {

    private static final Logger log = LoggerFactory.getLogger(TotpEnrolmentController.class);

    private final TotpEnrolmentService enrolmentService;

    public TotpEnrolmentController(TotpEnrolmentService enrolmentService) {
        this.enrolmentService = enrolmentService;
    }

    /** Begin enrolment — returns the otpauth:// provisioning URI + base32 secret for the app. */
    @PostMapping("/enrolment")
    public ResponseEntity<TotpEnrolmentService.EnrolmentResult> enrol(
            @RequestHeader("x-tenant-id") UUID tenantId,
            @RequestHeader("x-actor-id") String actorId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(enrolmentService.enrol(tenantId, actorId));
    }

    /** Confirm a code to activate the pending enrolment. */
    @PostMapping("/enrolment/confirm")
    public ResponseEntity<Map<String, Object>> confirm(
            @RequestHeader("x-tenant-id") UUID tenantId,
            @RequestHeader("x-actor-id") String actorId,
            @RequestBody Map<String, String> body) {
        String code = body == null ? null : body.get("code");
        try {
            boolean ok = enrolmentService.confirm(tenantId, actorId, code);
            if (ok) {
                return ResponseEntity.ok(Map.of("status", "ACTIVE"));
            }
            return ResponseEntity.badRequest().body(Map.of("status", "INVALID_CODE"));
        } catch (IllegalArgumentException e) {
            log.warn("TOTP confirm failed for actor {}: {}", actorId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /** Revoke the actor's enrolment. */
    @PostMapping("/enrolment/revoke")
    public ResponseEntity<Map<String, Object>> revoke(
            @RequestHeader("x-tenant-id") UUID tenantId,
            @RequestHeader("x-actor-id") String actorId) {
        enrolmentService.revoke(tenantId, actorId);
        return ResponseEntity.ok(Map.of("status", "REVOKED"));
    }
}
