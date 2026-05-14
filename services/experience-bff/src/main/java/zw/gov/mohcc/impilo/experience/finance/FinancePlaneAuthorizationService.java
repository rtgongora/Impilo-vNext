package zw.gov.mohcc.impilo.experience.finance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.client.TshepoAuthzServiceClient;

/**
 * Optional Tshepo PDP gate for enterprise-plane finance-domain BFF routes (synthetic paths map to
 * {@code billing-workspace} and {@code mushex-platform} resource types; see tshepo-authz V007).
 */
@Component
public class FinancePlaneAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(FinancePlaneAuthorizationService.class);

    private final TshepoAuthzServiceClient tshepoAuthzServiceClient;

    @Value("${impilo.security.allow-anonymous:false}")
    private boolean allowAnonymous;

    @Value("${impilo.finance.require-tshepo-authorize:false}")
    private boolean requireTshepoAuthorize;

    @Value("${impilo.finance.tshepo-pdp-fallback-allow:false}")
    private boolean tshepoPdpFallbackAllow;

    public FinancePlaneAuthorizationService(TshepoAuthzServiceClient tshepoAuthzServiceClient) {
        this.tshepoAuthzServiceClient = tshepoAuthzServiceClient;
    }

    public void assertBillingWorkspaceAccess(String method) {
        assertGate(method, "/internal/v1/finance/billing-workspace", "billing-workspace");
    }

    public void assertMushexPlatformAccess(String method) {
        assertGate(method, "/internal/v1/finance/mushex-platform", "mushex-platform");
    }

    public void assertCostaIntelAccess(String method) {
        assertGate(method, "/internal/v1/finance/costa-intel", "costa-intel");
    }

    /** COSTA pre-service / gate decisions ({@code /costa/v1/service-access-decisions}). */
    public void assertServiceAccessDecisionAccess(String method) {
        assertGate(method, "/internal/v1/finance/service-access-decisions", "service-access-decisions");
    }

    private void assertGate(String method, String syntheticPath, String label) {
        if (allowAnonymous) {
            return;
        }
        assertAuthenticated();
        if (!requireTshepoAuthorize) {
            return;
        }
        boolean allowed = tshepoAuthzServiceClient.financePlaneAllowed(method, syntheticPath);
        if (!allowed && !tshepoPdpFallbackAllow) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Tshepo PDP denied enterprise finance-domain access (" + label + ")");
        }
        if (!allowed) {
            log.warn("Tshepo PDP denied enterprise finance-domain access ({}) — proceeding due to tshepo-pdp-fallback-allow=true", label);
        }
    }

    private void assertAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }
}
