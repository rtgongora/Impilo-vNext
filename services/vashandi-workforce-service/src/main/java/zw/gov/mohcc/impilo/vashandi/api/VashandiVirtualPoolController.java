package zw.gov.mohcc.impilo.vashandi.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.vashandi.core.VirtualPoolDutyService;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Virtual-pool duty API for the virtual-hospital substrate. {@code poolId} is
 * the TUSO routing seam target_ref (frozen pool key). Read-only: coverage
 * truth lives in vsh_shift; the BFF composes it into virtual-hospital payloads
 * and the soft accept gate (duty is advisory, never blocks care).
 */
@RestController
public class VashandiVirtualPoolController {

    private final VirtualPoolDutyService dutyService;

    public VashandiVirtualPoolController(VirtualPoolDutyService dutyService) {
        this.dutyService = dutyService;
    }

    /** All virtual pools with rostered coverage for this tenant (management pool picker). */
    @GetMapping("/v1/internal/vashandi/virtual-pools")
    public List<Map<String, Object>> listPools(@RequestParam(value = "at", required = false) String at) {
        TrustContext ctx = TrustContextHolder.require();
        return dutyService.listPools(ctx.tenantId(), parseOrNow(at));
    }

    /** Every shift rostered against a pool, any status (management read). */
    @GetMapping("/v1/internal/vashandi/virtual-pools/{poolId}/shifts")
    public Map<String, Object> poolShifts(@PathVariable String poolId) {
        TrustContext ctx = TrustContextHolder.require();
        return dutyService.poolShifts(ctx.tenantId(), poolId);
    }

    /** Who is on duty for this pool right now (confirmed/checked_in, window covers now). */
    @GetMapping("/v1/internal/vashandi/virtual-pools/{poolId}/on-duty")
    public Map<String, Object> onDuty(@PathVariable String poolId,
                                      @RequestParam(value = "at", required = false) String at) {
        TrustContext ctx = TrustContextHolder.require();
        OffsetDateTime asOf = parseOrNow(at);
        return dutyService.onDuty(ctx.tenantId(), poolId, asOf);
    }

    /** Batch coverage counts + next rostered start for several pools. */
    @GetMapping("/v1/internal/vashandi/virtual-pools/coverage")
    public List<Map<String, Object>> coverage(@RequestParam("poolIds") String poolIds) {
        TrustContext ctx = TrustContextHolder.require();
        List<String> pools = Arrays.stream(poolIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        return dutyService.coverage(ctx.tenantId(), pools, OffsetDateTime.now());
    }

    private static OffsetDateTime parseOrNow(String raw) {
        if (raw == null || raw.isBlank()) return OffsetDateTime.now();
        try {
            return OffsetDateTime.parse(raw);
        } catch (Exception e) {
            return OffsetDateTime.now();
        }
    }
}
