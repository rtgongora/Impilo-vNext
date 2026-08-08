package zw.gov.mohcc.impilo.msikaflow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${impilo.security.disable-oauth-for-tests:false}") boolean disableOauthForTests,
            @Value("${msika-flow.dispatch-callback.allowed-clients:nhume-service}") String allowedCallbackClients)
            throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (disableOauthForTests) {
            // Estate/test convention (fail-closed, default false): every other
            // sovereign service honors this flag; msika-flow ignoring it made
            // BFF marketplace calls 401 in preview despite the env being set.
            http.authorizeHttpRequests(authz -> authz.anyRequest().permitAll());
            return http.build();
        }

        AuthorizationManager<RequestAuthorizationContext> dispatchCaller =
                callerIsOneOf(parseClients(allowedCallbackClients));

        http.authorizeHttpRequests(authz -> authz
                .requestMatchers(
                    "/actuator/**",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                .requestMatchers("/v1/internal/**").hasAuthority("SCOPE_internal")
                // ────────────────────────────────────────────────────────────────
                // Nhume delivery write-back callbacks.
                //
                // These were permitAll, justified as "authenticated at the Envoy ext_authz
                // mesh edge". That premise was false and was never true here: Envoy in this
                // estate is a single north-south deployment with routes for /api/v1/* — there
                // are no sidecars, and Nhume calls msika-flow-service:8100 pod-to-pod, so
                // ext_authz never saw these requests. Measured 2026-08-08: both paths executed
                // their handlers with no credential at all, from any pod in the namespace,
                // while an adjacent /internal/v1 path returned 401 — so the gate was live and
                // these two were simply outside it.
                //
                // That is consequential: the order callback drives the order state machine and
                // writes a courier chain-of-custody record, and the selection callback projects
                // proof-of-delivery and opens the escrow / refund seams.
                //
                // Pinned to the caller's own client, not merely "authenticated": any realm
                // token — including a human's — would otherwise be enough to report a delivery.
                // The identity is read from the validated JWT (azp / client_id), never from a
                // request header, which is forgeable.
                // ────────────────────────────────────────────────────────────────
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                        "/internal/v1/msika-flow/orders/*/dispatch-status").access(dispatchCaller)
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                        "/internal/v1/msika-flow/selections/*/delivery-status").access(dispatchCaller)
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

        return http.build();
    }

    static Set<String> parseClients(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Grants only to a validated workload token whose own client id is on the list.
     *
     * <p>Anonymous access is refused by the {@code instanceof} check, not by
     * {@code isAuthenticated()} — an unauthenticated request carries an
     * {@code AnonymousAuthenticationToken}, whose {@code isAuthenticated()} returns true. An
     * empty allow-list grants nothing, so a blanked-out configuration fails closed.
     */
    static AuthorizationManager<RequestAuthorizationContext> callerIsOneOf(Set<String> allowedClients) {
        return (authentication, context) -> {
            Authentication auth = authentication.get();
            if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
                return new AuthorizationDecision(false);
            }
            Jwt jwt = jwtAuth.getToken();
            String caller = jwt.getClaimAsString("azp");
            if (caller == null || caller.isBlank()) {
                caller = jwt.getClaimAsString("client_id");
            }
            return new AuthorizationDecision(
                    caller != null && allowedClients.contains(caller.trim().toLowerCase(Locale.ROOT)));
        };
    }
}
