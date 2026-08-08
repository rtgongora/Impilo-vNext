package zw.gov.mohcc.impilo.msikaflow.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.time.Instant;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Who may report a Nhume delivery milestone to Msika Flow.
 *
 * <p>These two callbacks were {@code permitAll} on the premise that Envoy ext_authz
 * authenticated them at a mesh edge. Measured 2026-08-08: there is no mesh edge — Envoy is a
 * single north-south deployment and Nhume calls msika-flow pod-to-pod — so both paths ran
 * their handlers with no credential at all, from any pod in the namespace. They drive the
 * order state machine, courier chain-of-custody, and the proof-of-delivery projection that
 * opens the escrow / refund seams.
 */
class InternalCallbackAuthorizationTest {

    private final AuthorizationManager<RequestAuthorizationContext> manager =
            SecurityConfig.callerIsOneOf(SecurityConfig.parseClients("nhume-service"));

    private static Supplier<Authentication> anonymous() {
        return () -> new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
    }

    private static Supplier<Authentication> tokenFrom(String claimName, String clientId) {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .claim(claimName, clientId)
                .claim("sub", "8171e231-8478-46b9-aed9-df5d0d05ba46")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        return () -> new JwtAuthenticationToken(jwt, AuthorityUtils.NO_AUTHORITIES);
    }

    private boolean granted(Supplier<Authentication> auth) {
        AuthorizationDecision decision = manager.check(auth, null);
        return decision != null && decision.isGranted();
    }

    @Test
    void anUnauthenticatedCallerIsRefused() {
        // isAuthenticated() returns TRUE on an AnonymousAuthenticationToken, so a check
        // written against it would have granted this. The token type is what decides.
        assertThat(granted(anonymous())).isFalse();
    }

    @Test
    void aNullAuthenticationIsRefused() {
        assertThat(granted(() -> null)).isFalse();
    }

    @Test
    void nhumesOwnWorkloadTokenIsAdmitted() {
        assertThat(granted(tokenFrom("azp", "nhume-service"))).isTrue();
    }

    @Test
    void theClientIdClaimIsHonouredWhenAzpIsAbsent() {
        assertThat(granted(tokenFrom("client_id", "nhume-service"))).isTrue();
    }

    @Test
    void someOtherServicesValidTokenIsRefused() {
        // Authenticated is not enough: only the courier may report a courier milestone.
        assertThat(granted(tokenFrom("azp", "vashandi-workforce-service"))).isFalse();
    }

    @Test
    void aValidTokenNamingNoClientAtAllIsRefused() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256")
                .claim("sub", "a-human")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        assertThat(granted(() -> new JwtAuthenticationToken(jwt, AuthorityUtils.NO_AUTHORITIES)))
                .isFalse();
    }

    @Test
    void anEmptyAllowListGrantsNothing() {
        AuthorizationManager<RequestAuthorizationContext> closed =
                SecurityConfig.callerIsOneOf(SecurityConfig.parseClients(""));
        assertThat(closed.check(tokenFrom("azp", "nhume-service"), null).isGranted()).isFalse();
    }

    @Test
    void theAllowListIsCaseInsensitiveAndTolerantOfWhitespace() {
        Set<String> parsed = SecurityConfig.parseClients(" Nhume-Service , other-service ");
        assertThat(parsed).containsExactlyInAnyOrder("nhume-service", "other-service");

        AuthorizationManager<RequestAuthorizationContext> multi = SecurityConfig.callerIsOneOf(parsed);
        assertThat(multi.check(tokenFrom("azp", "NHUME-SERVICE"), null).isGranted()).isTrue();
        assertThat(multi.check(tokenFrom("azp", "unlisted"), null).isGranted()).isFalse();
    }
}
