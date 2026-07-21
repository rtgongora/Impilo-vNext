package zw.gov.mohcc.impilo.vito.config;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import zw.gov.mohcc.impilo.shared.auth.TrustContextFilter;

/**
 * VITO security configuration implementing the Tshepo-VITO Handshake.
 *
 * Dual-mode access (Sovereign Service 2):
 *
 * INTERNAL MODE (platform services via Envoy):
 *   Envoy ext_authz → TSHEPO validates → trust headers injected.
 *   Full access to identity operations: issuance, recovery, merge, card, wallet.
 *
 * EXTERNAL MODE (3rd-party identity consumers):
 *   External systems (civil registrars, insurance, etc.) with Tshepo-scoped JWT.
 *   Read-only identity verification, OpenCR $match, demographic lookup.
 *   No write access to cards, wallets, or identity merges.
 *
 * Preview/test profile ({@code impilo.security.disable-oauth-for-tests=true}) opens
 * {@code /v1/client-registry/**} for first-party BFF S2S with trust headers only.
 * Production chain always requires authenticated JWT for business APIs.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public TrustContextFilter trustContextFilter(ObjectMapper objectMapper) {
        return new TrustContextFilter(objectMapper);
    }

    /**
     * Preview/test chain: the internal service-plane + client-registry paths are authed by
     * TRUST HEADERS ONLY (first-party BFF S2S); business paths require an authenticated JWT.
     *
     * A SINGLE chain (not a securityMatcher split — MvcRequestMatcher matched those paths
     * inconsistently) plus a path-scoped {@link #s2sBearerTokenResolver} that makes the
     * relayed browser Bearer INVISIBLE on the S2S paths. That is the precise fix for the
     * regression from 1089af58d enabling JWT processing: Spring's bearer filter validates a
     * present token before the permitAll rule runs, so a relayed issuer-mismatched/expired
     * user token 401'd these permitAll paths (the "patient registry unreachable" symptom).
     * With the token hidden on S2S paths, permitAll applies; business paths still validate.
     * Flag-gated — never active when {@code impilo.security.disable-oauth-for-tests=false}.
     */
    @Bean
    @ConditionalOnProperty(name = "impilo.security.disable-oauth-for-tests", havingValue = "true")
    public SecurityFilterChain testFilterChain(HttpSecurity http, TrustContextFilter trustContextFilter,
            @org.springframework.beans.factory.annotation.Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(trustContextFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                // Internal service-plane + client-registry: first-party BFF S2S authed by
                // trust headers only. VITO uses BOTH path conventions — /internal/v1/**
                // (V11 patients, legacy-phid) AND /v1/internal/** (client search, dedup,
                // issuance, audit). Permit both, or BFF patient search 401s.
                .requestMatchers("/v1/client-registry/**").permitAll()
                .requestMatchers("/internal/v1/**").permitAll()
                .requestMatchers("/v1/internal/**").permitAll()
                .anyRequest().authenticated());

        // JWT for business paths; the resolver hides any relayed Bearer on the permitAll S2S
        // paths so it is never validated-and-rejected there.
        if (issuerUri != null && !issuerUri.isBlank()) {
            http.oauth2ResourceServer(oauth2 -> oauth2
                    .bearerTokenResolver(s2sBearerTokenResolver())
                    .jwt(jwt -> {}));
        }

        return http.build();
    }

    /**
     * Returns {@code null} (no token) for the trust-header S2S paths so a relayed browser
     * Bearer is ignored there rather than validated; all other paths use the default
     * resolver, keeping JWT enforcement on business endpoints.
     */
    private static org.springframework.security.oauth2.server.resource.web.BearerTokenResolver s2sBearerTokenResolver() {
        org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver delegate =
                new org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver();
        return request -> {
            String path = request.getRequestURI();
            if (path != null && (path.startsWith("/v1/client-registry/")
                    || path.startsWith("/v1/internal/")
                    || path.startsWith("/internal/v1/")
                    || path.startsWith("/actuator/")
                    || path.startsWith("/v3/api-docs")
                    || path.startsWith("/swagger-ui"))) {
                return null;
            }
            return delegate.resolve(request);
        };
    }

    @Bean
    @ConditionalOnProperty(name = "impilo.security.disable-oauth-for-tests", havingValue = "false", matchIfMissing = true)
    public SecurityFilterChain productionFilterChain(HttpSecurity http, TrustContextFilter trustContextFilter,
            @org.springframework.beans.factory.annotation.Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(trustContextFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                .anyRequest().authenticated()
            );

        if (issuerUri != null && !issuerUri.isBlank()) {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        }

        return http.build();
    }
}
