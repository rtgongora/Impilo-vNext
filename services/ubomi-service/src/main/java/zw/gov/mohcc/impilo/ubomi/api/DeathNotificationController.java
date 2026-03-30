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

    @PostMapping("/{notificationId}/register")
    public ResponseEntity<ApiResponse<DeathNotificationEntity>> registerDeath(
            @PathVariable Long notificationId) {
        TrustContext ctx = TrustContextHolder.require();

        if (ctx.mode() == AccessMode.EXTERNAL) {
            return ResponseEntity.status(403).body(
                ApiResponse.error("FORBIDDEN", "External consumers cannot register deaths", 403, ctx.correlationId().toString())
            );
        }

        DeathNotificationEntity registered = deathNotificationService.register(ctx.tenantId(), notificationId);

        return ResponseEntity.ok(
            ApiResponse.ok(registered, ctx.correlationId().toString())
        );
    }
}
