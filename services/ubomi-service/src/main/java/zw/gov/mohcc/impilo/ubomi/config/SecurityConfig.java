package zw.gov.mohcc.impilo.ubomi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import zw.gov.mohcc.impilo.shared.auth.TrustContextFilter;

/**
 * Ubomi security configuration implementing the Tshepo-Ubomi Handshake.
 *
 * Dual-mode access:
 *
 * INTERNAL MODE (platform services via Envoy):
 *   Envoy has already called TSHEPO ext_authz and validated the request.
 *   Trust headers are injected by Envoy. TrustContextFilter extracts them.
 *   JWT validation still applies (defense-in-depth).
 *
 * EXTERNAL MODE (civil registrar systems):
 *   External CRVS systems authenticate with Keycloak, receive a Tshepo-scoped JWT.
 *   JWT must contain the 'ubomi:read' or 'ubomi:notify' scope.
 *   TrustContextFilter sets AccessMode.EXTERNAL so controllers can
 *   restrict write operations to authorized civil registrar endpoints.
 *
 * Endpoint access matrix:
 *   /v1/births/**          — authenticated (both modes)
 *   /v1/deaths/**          — authenticated (both modes)
 *   /v1/verifications/**   — authenticated (both modes)
 *   /actuator/health       — open (probes)
 *   /v3/api-docs/**        — open (documentation)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public TrustContextFilter trustContextFilter() {
        return new TrustContextFilter();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(trustContextFilter(), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // Probes and documentation — open
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                // All API endpoints require a valid Tshepo-issued JWT
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

        return http.build();
    }
}
