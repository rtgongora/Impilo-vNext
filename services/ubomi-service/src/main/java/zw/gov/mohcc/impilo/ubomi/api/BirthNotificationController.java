package zw.gov.mohcc.impilo.ubomi.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

/**
 * Birth Notification API — the Tshepo-Ubomi handshake in action.
 *
 * Facilities and civil registrar systems can submit birth notifications:
 *   1. Authenticate with Keycloak (OIDC) to get a Tshepo-scoped JWT
 *   2. POST /v1/births with the birth notification payload
 *   3. Ubomi validates against civil registry rules and stores the event
 *   4. On approval, publishes BIRTH_REGISTERED event to Kafka
 *      so VITO can issue the newborn's Impilo ID
 *
 * External civil registrar systems can query birth status via GET endpoints.
 * Only INTERNAL consumers can approve/reject birth notifications.
 */
@RestController
@RequestMapping("/v1/births")
public class BirthNotificationController {

    /**
     * List birth notifications with filtering and pagination.
     * Available to both INTERNAL and EXTERNAL consumers.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<String>> listBirthNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        TrustContext ctx = TrustContextHolder.require();

        // TODO: delegate to BirthNotificationService for paginated lookup
        return ResponseEntity.ok(
            ApiResponse.ok("Birth notification list placeholder — mode: " + ctx.mode(), ctx.correlationId().toString())
        );
    }

    /**
     * Submit a birth notification.
     * Both INTERNAL (facility EHR) and EXTERNAL (civil registrar) can submit.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<String>> submitBirthNotification(@RequestBody String body) {
        TrustContext ctx = TrustContextHolder.require();

        // TODO: delegate to BirthNotificationService
        return ResponseEntity.ok(
            ApiResponse.ok("Birth notification submitted — mode: " + ctx.mode(), ctx.correlationId().toString())
        );
    }

    /**
     * Approve or reject a birth notification.
     * INTERNAL mode only — external consumers cannot approve registrations.
     */
    @PostMapping("/{notificationId}/approve")
    public ResponseEntity<ApiResponse<String>> approveBirthNotification(
            @PathVariable String notificationId) {
        TrustContext ctx = TrustContextHolder.require();

        if (ctx.mode() == AccessMode.EXTERNAL) {
            return ResponseEntity.status(403).body(
                ApiResponse.error("FORBIDDEN", "External consumers cannot approve birth notifications", 403, ctx.correlationId().toString())
            );
        }

        // TODO: delegate to BirthNotificationService
        return ResponseEntity.ok(
            ApiResponse.ok("Birth notification " + notificationId + " approved", ctx.correlationId().toString())
        );
    }
}
