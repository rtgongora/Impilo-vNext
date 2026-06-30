package zw.gov.mohcc.impilo.ubomi.api;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.ubomi.core.DeathNotificationService;
import zw.gov.mohcc.impilo.ubomi.persistence.entity.DeathNotificationEntity;

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

    private final DeathNotificationService deathNotificationService;

    public DeathNotificationController(DeathNotificationService deathNotificationService) {
        this.deathNotificationService = deathNotificationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<DeathNotificationEntity>>> listDeathNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        TrustContext ctx = TrustContextHolder.require();
        Page<DeathNotificationEntity> results = deathNotificationService.list(ctx.tenantId(), page, size);

        return ResponseEntity.ok(
            ApiResponse.ok(results, ctx.correlationId().toString())
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DeathNotificationEntity>> submitDeathNotification(
            @RequestBody DeathNotificationEntity body) {
        TrustContext ctx = TrustContextHolder.require();

        body.setTenantId(ctx.tenantId());
        body.setNotifiedByActor(ctx.actorId());
        if (ctx.facilityId() != null) {
            body.setFacilityId(ctx.facilityId());
        }

        DeathNotificationEntity saved = deathNotificationService.submit(body);

        return ResponseEntity.ok(
            ApiResponse.ok(saved, ctx.correlationId().toString())
        );
    }

    /**
     * Certify cause of death — requires medical practitioner authorization.
     * INTERNAL mode only.
     */
    @PostMapping("/{notificationId}/certify")
    public ResponseEntity<ApiResponse<DeathNotificationEntity>> certifyDeath(
            @PathVariable Long notificationId) {
        TrustContext ctx = TrustContextHolder.require();

        if (ctx.mode() == AccessMode.EXTERNAL) {
            return ResponseEntity.status(403).body(
                ApiResponse.error("FORBIDDEN", "External consumers cannot certify deaths", 403, ctx.correlationId().toString())
            );
        }

        DeathNotificationEntity certified = deathNotificationService.certify(
                ctx.tenantId(), notificationId, ctx.actorId(), "DOCTOR");

        return ResponseEntity.ok(
            ApiResponse.ok(certified, ctx.correlationId().toString())
        );
    }

    /**
     * Stage the civil-registration package for the Registrar General (WS#8). Validates required
     * fields; does not forge a registration.
     */
    @PostMapping("/{notificationId}/package-ready")
    public ResponseEntity<ApiResponse<DeathNotificationEntity>> markPackageReady(
            @PathVariable Long notificationId) {
        TrustContext ctx = TrustContextHolder.require();
        if (ctx.mode() == AccessMode.EXTERNAL) {
            return ResponseEntity.status(403).body(
                ApiResponse.error("FORBIDDEN", "External consumers cannot stage CRVS packages", 403, ctx.correlationId().toString())
            );
        }
        DeathNotificationEntity e = deathNotificationService.markPackageReady(ctx.tenantId(), notificationId);
        return ResponseEntity.ok(ApiResponse.ok(e, ctx.correlationId().toString()));
    }

    /**
     * Record a real civil-registration outcome from the Registrar General (owner-routed). Requires a
     * civil registration number — REGISTERED is never synthesised.
     */
    @PostMapping("/{notificationId}/register")
    public ResponseEntity<ApiResponse<DeathNotificationEntity>> register(
            @PathVariable Long notificationId, @RequestBody java.util.Map<String, String> body) {
        TrustContext ctx = TrustContextHolder.require();
        if (ctx.mode() == AccessMode.EXTERNAL) {
            return ResponseEntity.status(403).body(
                ApiResponse.error("FORBIDDEN", "External consumers cannot register deaths", 403, ctx.correlationId().toString())
            );
        }
        DeathNotificationEntity e = deathNotificationService.register(
                ctx.tenantId(), notificationId, body.get("civilRegNumber"));
        return ResponseEntity.ok(ApiResponse.ok(e, ctx.correlationId().toString()));
    }
}
