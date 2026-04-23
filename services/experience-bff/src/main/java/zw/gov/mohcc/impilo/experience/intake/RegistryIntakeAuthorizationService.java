package zw.gov.mohcc.impilo.experience.intake;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.client.TshepoAuthzServiceClient;

import java.util.HashSet;
import java.util.Set;

/**
 * Coarse RBAC (JWT realm roles) plus optional Tshepo PDP for registry intake mutations.
 */
@Component
public class RegistryIntakeAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(RegistryIntakeAuthorizationService.class);

    private static final Set<String> SESSION_MUTATION_ROLES = Set.of(
            "SYSTEM_ADMIN", "HIE_ADMIN", "REGISTRY_ADMIN", "IDENTITY_STEWARD", "IMPORT_ADMIN", "DEVELOPER");

    private static final Set<String> IMPORT_EXECUTE_ROLES = Set.of(
            "SYSTEM_ADMIN", "HIE_ADMIN", "REGISTRY_ADMIN", "IMPORT_ADMIN", "DEVELOPER");

    private final TshepoAuthzServiceClient tshepoAuthzServiceClient;

    @Value("${impilo.security.allow-anonymous:false}")
    private boolean allowAnonymous;

    @Value("${impilo.registry-intake.tshepo-pdp-enabled:false}")
    private boolean tshepoPdpEnabled;

    @Value("${impilo.registry-intake.tshepo-pdp-fallback-allow:false}")
    private boolean tshepoPdpFallbackAllow;

    public RegistryIntakeAuthorizationService(TshepoAuthzServiceClient tshepoAuthzServiceClient) {
        this.tshepoAuthzServiceClient = tshepoAuthzServiceClient;
    }

    public void assertAuthenticated() {
        if (allowAnonymous) {
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }

    public void assertSessionMutation() {
        assertAuthenticated();
        assertRealmRoles(SESSION_MUTATION_ROLES, "registry intake session/recovery");
        assertTshepoPdp("SESSION");
    }

    public void assertImportPreview() {
        assertAuthenticated();
        assertRealmRoles(SESSION_MUTATION_ROLES, "registry import preview");
        assertTshepoPdp("IMPORT_PREVIEW");
    }

    public void assertImportExecute() {
        assertAuthenticated();
        assertRealmRoles(IMPORT_EXECUTE_ROLES, "registry import execute");
        assertTshepoPdp("IMPORT_EXECUTE");
    }

    public void assertProviderOrchestration() {
        assertAuthenticated();
        assertRealmRoles(SESSION_MUTATION_ROLES, "provider intake orchestration");
        assertTshepoPdp("PROVIDER_ORCH");
    }

    private void assertRealmRoles(Set<String> allowed, String actionLabel) {
        if (allowAnonymous) {
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Set<String> roles = new HashSet<>();
        for (GrantedAuthority a : auth.getAuthorities()) {
            String r = a.getAuthority();
            if (r.startsWith("ROLE_")) {
                roles.add(r.substring("ROLE_".length()));
            } else {
                roles.add(r);
            }
        }
        boolean ok = roles.stream().anyMatch(allowed::contains);
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Insufficient role for " + actionLabel + " — requires one of: " + allowed);
        }
    }

    private void assertTshepoPdp(String phase) {
        if (allowAnonymous) {
            return;
        }
        if (!tshepoPdpEnabled) {
            return;
        }
        boolean allowed = tshepoAuthzServiceClient.registryIntakeMutationAllowed();
        if (!allowed && !tshepoPdpFallbackAllow) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Tshepo PDP denied registry intake mutation (phase=" + phase + ")");
        }
        if (!allowed) {
            log.warn("Tshepo PDP denied registry intake (phase={}) — proceeding due to fallback-allow", phase);
        }
    }
}
