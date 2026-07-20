package zw.gov.mohcc.impilo.abis.config;

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
 * Security configuration for the ABIS service.
 *
 * <p>By default (production / full-preview behind Keycloak) all /v1/abis/** endpoints
 * require a valid JWT bearer token (issued by Keycloak via Envoy). Actuator
 * health/info/prometheus and OpenAPI docs are open for infrastructure probes and
 * developer tooling.</p>
 *
 * <p>Local Compose has no Keycloak: set {@code impilo.security.oauth2.enabled=false}
 * (env {@code IMPILO_SECURITY_OAUTH2_ENABLED=false}, the estate convention used by
 * pct-service / integration-hub) to boot with an open chain and no JwtDecoder — the
 * OAuth2 resource-server autoconfig can then be excluded without a startup failure.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Estate-standard trust context extraction (Envoy ext_authz header contract —
     * X-Tenant-ID, X-Actor-ID, X-Purpose-Of-Use, X-Correlation-ID per
     * {@code CompanionHeaders}). Populates {@code TrustContextHolder} for every
     * request in BOTH chains, so tenant/actor context is available to matching,
     * adjudication, and audit paths regardless of the OAuth2 toggle.
     */
    @Bean
    public TrustContextFilter trustContextFilter(ObjectMapper objectMapper) {
        return new TrustContextFilter(objectMapper);
    }

    /**
     * Production / full-preview: JWT-authenticated resource server. Active unless
     * {@code impilo.security.oauth2.enabled=false}. Biometric template endpoints are
     * NEVER anonymously reachable in this chain.
     */
    @Bean
    @ConditionalOnProperty(name = "impilo.security.oauth2.enabled", havingValue = "true", matchIfMissing = true)
    public SecurityFilterChain jwtFilterChain(HttpSecurity http, TrustContextFilter trustContextFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(trustContextFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // Actuator probes
                .requestMatchers("/actuator/health", "/actuator/health/**",
                        "/actuator/info", "/actuator/prometheus").permitAll()
                // OpenAPI
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // Everything else requires JWT
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

        return http.build();
    }

    /**
     * Local Compose (no Keycloak): open chain, no JwtDecoder required. Trust headers
     * (X-Tenant-ID) still flow through — matching is fail-closed regardless.
     */
    @Bean
    @ConditionalOnProperty(name = "impilo.security.oauth2.enabled", havingValue = "false")
    public SecurityFilterChain openFilterChain(HttpSecurity http, TrustContextFilter trustContextFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(trustContextFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}
