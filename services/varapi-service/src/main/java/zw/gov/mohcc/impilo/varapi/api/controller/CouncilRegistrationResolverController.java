package zw.gov.mohcc.impilo.varapi.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.varapi.core.CouncilRegistrationResolverService;
import zw.gov.mohcc.impilo.varapi.core.CouncilRegistrationResolverService.CouncilProviderRef;

import java.util.Optional;

/**
 * Council / EC-number resolver (L3 provider lane).
 *
 * <p>{@code GET /v1/internal/providers/resolve-council-number?registrationNumber=…&councilCode=…}
 * resolves a council registration number to its provider's person anchor for the
 * silent identifier-resolution chain (consumed by tshepo-identity). The number
 * <b>resolves a profile but never authenticates</b>: the response always reports
 * {@code canAuthenticate=false} (LOGIN-PROVIDERID-DENY, specced to track P).</p>
 *
 * <p>Internal route behind the Envoy ext_authz → TSHEPO gate. On a miss it returns
 * an empty-resolution body (not a 404) so callers can apply a uniform
 * anti-enumeration response.</p>
 */
@RestController
@RequestMapping("/v1/internal/providers")
public class CouncilRegistrationResolverController {

    private static final Logger log = LoggerFactory.getLogger(CouncilRegistrationResolverController.class);

    private final CouncilRegistrationResolverService resolverService;

    public CouncilRegistrationResolverController(CouncilRegistrationResolverService resolverService) {
        this.resolverService = resolverService;
    }

    @GetMapping("/resolve-council-number")
    public ResponseEntity<ApiResponse<ResolveCouncilNumberResponse>> resolve(
            @RequestParam("registrationNumber") String registrationNumber,
            @RequestParam(value = "councilCode", required = false) String councilCode) {
        TrustContext ctx = TrustContextHolder.require();
        Optional<CouncilProviderRef> ref =
                resolverService.resolve(ctx.tenantId(), councilCode, registrationNumber);
        log.debug("council-number resolution requested (result present={})", ref.isPresent());
        ResolveCouncilNumberResponse body = ref
                .map(r -> new ResolveCouncilNumberResponse(true, r))
                .orElseGet(() -> new ResolveCouncilNumberResponse(false, null));
        return ResponseEntity.ok(ApiResponse.ok(body, ctx.correlationId().toString()));
    }

    public record ResolveCouncilNumberResponse(boolean resolved, CouncilProviderRef providerRef) {
    }
}
