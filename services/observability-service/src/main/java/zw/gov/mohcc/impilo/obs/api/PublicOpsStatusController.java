package zw.gov.mohcc.impilo.obs.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.obs.core.OpsService;
import zw.gov.mohcc.impilo.obs.core.PublicServiceStatusSummary;

/**
 * Public, disclosure-limited service-status read (ADR §3 {@code Public*Controller}).
 *
 * <p>Returns a clean typed projection — service name, derived UP/STALE liveness,
 * and last heartbeat only. It never exposes instance ids, tenant, version,
 * metadata, instance counts, or raw stored status strings, and never invents
 * DEGRADED/DOWN states.</p>
 */
@RestController
@RequestMapping("/v1/public/ops")
public class PublicOpsStatusController {

    private final OpsService opsService;

    public PublicOpsStatusController(OpsService opsService) {
        this.opsService = opsService;
    }

    @GetMapping("/status")
    public ResponseEntity<PublicServiceStatusSummary> getStatus() {
        return ResponseEntity.ok(opsService.getPublicServiceStatus());
    }
}
