package zw.gov.mohcc.impilo.experience.intelligence;

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
 * Optional Tshepo PDP gate for the Health Intelligence plane (synthetic path
 * {@code POST /internal/v1/intelligence-plane} → resource {@code intelligence-plane}).
 */
@Component
public class HealthIntelligenceAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(HealthIntelligenceAuthorizationService.class);

    private final TshepoAuthzServiceClient tshepoAuthzServiceClient;

    @Value("${impilo.security.allow-anonymous:false}")
    private boolean allowAnonymous;

    @Value("${impilo.intelligence.require-tshepo-authorize:false}")
    private boolean requireTshepoAuthorize;

    @Value("${impilo.intelligence.tshepo-pdp-fallback-allow:true}")
    private boolean tshepoPdpFallbackAllow;

    public HealthIntelligenceAuthorizationService(TshepoAuthzServiceClient tshepoAuthzServiceClient) {
        this.tshepoAuthzServiceClient = tshepoAuthzServiceClient;
    }

    public void assertIntelligencePlaneAccess(String phase) {
        if (allowAnonymous) {
            return;
        }
        assertAuthenticated();
        if (!requireTshepoAuthorize) {
            return;
        }
        boolean allowed = tshepoAuthzServiceClient.intelligencePlaneQueryAllowed();
        if (!allowed && !tshepoPdpFallbackAllow) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Tshepo PDP denied intelligence plane access (phase=" + phase + ")");
        }
        if (!allowed) {
            log.warn("Tshepo PDP denied intelligence plane (phase={}) — proceeding due to tshepo-pdp-fallback-allow=true",
                    phase);
        }
    }

    private void assertAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }
}
