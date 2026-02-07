package zw.gov.mohcc.impilo.ubomi.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

/**
 * Death Notification API.
 *
 * Facilities submit death notifications which trigger:
 *   1. VITO client status update (DECEASED flag on Impilo ID)
 *   2. SHR encounter closure in BUTANO
 *   3. Civil registry death certificate issuance workflow
 *
 * External civil registrar systems can query death records.
 * Only INTERNAL consumers can certify cause-of-death.
 */
@RestController
@RequestMapping("/v1/deaths")
public class DeathNotificationController {

    @GetMapping
    public ResponseEntity<ApiResponse<String>> listDeathNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        TrustContext ctx = TrustContextHolder.require();

        // TODO: delegate to DeathNotificationService
        return ResponseEntity.ok(
            ApiResponse.ok("Death notification list placeholder — mode: " + ctx.mode(), ctx.correlationId().toString())
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<String>> submitDeathNotification(@RequestBody String body) {
        TrustContext ctx = TrustContextHolder.require();

        // TODO: delegate to DeathNotificationService
        return ResponseEntity.ok(
            ApiResponse.ok("Death notification submitted — mode: " + ctx.mode(), ctx.correlationId().toString())
        );
    }

    /**
     * Certify cause of death — requires medical practitioner authorization.
     * INTERNAL mode only.
     */
    @PostMapping("/{notificationId}/certify")
    public ResponseEntity<ApiResponse<String>> certifyDeath(
            @PathVariable String notificationId) {
        TrustContext ctx = TrustContextHolder.require();

        if (ctx.mode() == AccessMode.EXTERNAL) {
            return ResponseEntity.status(403).body(
                ApiResponse.error("FORBIDDEN", "External consumers cannot certify deaths", 403, ctx.correlationId().toString())
            );
        }

        // TODO: delegate to DeathNotificationService
        return ResponseEntity.ok(
            ApiResponse.ok("Death " + notificationId + " certified", ctx.correlationId().toString())
        );
    }
}
