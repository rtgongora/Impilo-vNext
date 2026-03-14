package zw.gov.mohcc.impilo.ubomi.api;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.ubomi.core.BirthNotificationService;
import zw.gov.mohcc.impilo.ubomi.persistence.entity.BirthNotificationEntity;

import com.fasterxml.jackson.databind.ObjectMapper;

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

    private final BirthNotificationService birthNotificationService;
    private final ObjectMapper objectMapper;

    public BirthNotificationController(BirthNotificationService birthNotificationService,
                                        ObjectMapper objectMapper) {
        this.birthNotificationService = birthNotificationService;
        this.objectMapper = objectMapper;
    }

    /**
     * List birth notifications with filtering and pagination.
     * Available to both INTERNAL and EXTERNAL consumers.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<BirthNotificationEntity>>> listBirthNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        TrustContext ctx = TrustContextHolder.require();
        Page<BirthNotificationEntity> results = birthNotificationService.list(ctx.tenantId(), page, size);

        return ResponseEntity.ok(
            ApiResponse.ok(results, ctx.correlationId().toString())
        );
    }

    /**
     * Submit a birth notification.
     * Both INTERNAL (facility EHR) and EXTERNAL (civil registrar) can submit.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<BirthNotificationEntity>> submitBirthNotification(
            @RequestBody BirthNotificationEntity body) {
        TrustContext ctx = TrustContextHolder.require();

        body.setTenantId(ctx.tenantId());
        body.setNotifiedByActor(ctx.actorId());
        if (ctx.facilityId() != null) {
            body.setFacilityId(ctx.facilityId());
        }

        BirthNotificationEntity saved = birthNotificationService.submit(body);

        return ResponseEntity.ok(
            ApiResponse.ok(saved, ctx.correlationId().toString())
        );
    }

    /**
     * Approve or reject a birth notification.
     * INTERNAL mode only — external consumers cannot approve registrations.
     */
    @PostMapping("/{notificationId}/approve")
    public ResponseEntity<ApiResponse<BirthNotificationEntity>> approveBirthNotification(
            @PathVariable Long notificationId) {
        TrustContext ctx = TrustContextHolder.require();

        if (ctx.mode() == AccessMode.EXTERNAL) {
            return ResponseEntity.status(403).body(
                ApiResponse.error("FORBIDDEN", "External consumers cannot approve birth notifications", 403, ctx.correlationId().toString())
            );
        }

        BirthNotificationEntity approved = birthNotificationService.approve(ctx.tenantId(), notificationId);

        return ResponseEntity.ok(
            ApiResponse.ok(approved, ctx.correlationId().toString())
        );
    }
}
