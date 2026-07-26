package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.varapi.core.ProviderDisplayResolutionService;

import java.util.List;
import java.util.Map;

/**
 * Batch display-facts resolution for the experience composition layer.
 *
 * <p>The staffing surfaces (on-call rota, roster week, swap picker) hold vashandi worker
 * references, because {@code vsh_workforce_profile} keeps no name and no telephone number — person
 * identity is vito's and provider identity is varapi's. Rather than copy names into the rostering
 * service, the BFF composes them: it asks here for the display facts of the people on a rota, in
 * one call per screen.</p>
 *
 * <p>INTERNAL-only, like the other registry resolvers. Unknown and malformed ids come back
 * {@code resolved=false} in the same shape as a hit, so the caller renders the worker reference it
 * already has rather than an invented name.</p>
 */
@RestController
@RequestMapping("/v1/internal/providers/by-health-id")
public class ProviderDisplayResolutionController {

    private final ProviderDisplayResolutionService displayResolutionService;

    public ProviderDisplayResolutionController(ProviderDisplayResolutionService displayResolutionService) {
        this.displayResolutionService = displayResolutionService;
    }

    public record ResolveDisplayRequest(List<String> healthIds) {}

    @PostMapping("/resolve-display")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> resolveDisplay(
            @RequestBody ResolveDisplayRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        if (ctx.mode() != AccessMode.INTERNAL) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "INTERNAL_ONLY");
        }
        List<Map<String, Object>> rows = displayResolutionService.resolveDisplayFacts(
                request == null ? List.of() : request.healthIds());
        return ResponseEntity.ok(ApiResponse.ok(rows, ctx.correlationId().toString()));
    }
}
