package zw.gov.mohcc.impilo.vashandi.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.vashandi.core.WorkforceIdentityRefService;

import java.util.List;
import java.util.Map;

/**
 * Batch profile → person-anchor lookup for the experience composition layer.
 *
 * <p>The rota carries {@code workforce_profile_id} and the operational {@code staff_reference},
 * because vashandi holds no names. To show a person's name the composition layer needs the anchor
 * the identity registries are keyed by — the Health ID — for a page's worth of profiles at once.
 * This returns that mapping and nothing else: no name, no telephone number, no employment detail.
 * Vashandi does not acquire an identity surface by being asked who someone is.</p>
 */
@RestController
@RequestMapping("/v1/internal/vashandi/workforce-profiles")
public class WorkforceIdentityRefController {

    private final WorkforceIdentityRefService identityRefService;

    public WorkforceIdentityRefController(WorkforceIdentityRefService identityRefService) {
        this.identityRefService = identityRefService;
    }

    /**
     * {@code GET /identity-refs?ids=<uuid>,<uuid>} — one row per profile that exists in this
     * tenant. Unknown ids are simply absent; the caller keeps the reference it already has.
     */
    @GetMapping("/identity-refs")
    public Map<String, Object> identityRefs(@RequestParam(name = "ids", required = false) String ids) {
        TrustContext ctx = TrustContextHolder.require();
        List<Map<String, Object>> rows = identityRefService.identityRefs(ctx.tenantId(), ids);
        return Map.of("data", rows);
    }
}
