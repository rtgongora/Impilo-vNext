package zw.gov.mohcc.impilo.experience.shell;

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
 * Optional Tshepo PDP gate for shell workspace persistence (synthetic path
 * {@code /internal/v1/shell/workspace-state}).
 */
@Component
public class ShellWorkspaceAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(ShellWorkspaceAuthorizationService.class);

    private final TshepoAuthzServiceClient tshepoAuthzServiceClient;

    @Value("${impilo.security.allow-anonymous:false}")
    private boolean allowAnonymous;

    @Value("${impilo.shell-workspace.require-tshepo-authorize:false}")
    private boolean requireTshepoAuthorize;

    @Value("${impilo.shell-workspace.tshepo-pdp-fallback-allow:true}")
    private boolean tshepoPdpFallbackAllow;

    public ShellWorkspaceAuthorizationService(TshepoAuthzServiceClient tshepoAuthzServiceClient) {
        this.tshepoAuthzServiceClient = tshepoAuthzServiceClient;
    }

    public void assertShellWorkspaceAccess(String httpMethod) {
        if (allowAnonymous) {
            return;
        }
        assertAuthenticated();
        if (!requireTshepoAuthorize) {
            return;
        }
        boolean allowed = tshepoAuthzServiceClient.shellWorkspaceStateAllowed(httpMethod);
        if (!allowed && !tshepoPdpFallbackAllow) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Tshepo PDP denied shell workspace access (" + httpMethod + ")");
        }
        if (!allowed) {
            log.warn("Tshepo PDP denied shell workspace (method={}) — proceeding due to tshepo-pdp-fallback-allow=true",
                    httpMethod);
        }
    }

    private void assertAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }
}
